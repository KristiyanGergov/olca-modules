package org.openlca.git.actions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.openlca.core.database.IDatabase;
import org.openlca.core.library.Library;
import org.openlca.git.GitConfig;
import org.openlca.git.ObjectIdStore;
import org.openlca.git.actions.ConflictResolver.ConflictResolutionType;
import org.openlca.git.actions.ImportHelper.ImportResult;
import org.openlca.git.find.Commits;
import org.openlca.git.model.Change;
import org.openlca.git.model.Commit;
import org.openlca.git.model.DiffType;
import org.openlca.git.util.Constants;
import org.openlca.git.util.Diffs;
import org.openlca.git.util.GitStoreReader;
import org.openlca.git.util.History;
import org.openlca.git.util.Repositories;
import org.openlca.git.writer.CommitWriter;

public class GitMerge extends GitProgressAction<Boolean> {

	private final Repository git;
	private final History history;
	private final Commits commits;
	private IDatabase database;
	private ObjectIdStore workspaceIds;
	private PersonIdent committer;
	private ConflictResolver conflictResolver;
	private LibraryResolver libraryResolver;
	private boolean applyStash;

	private GitMerge(Repository git) {
		this.git = git;
		this.history = History.of(git);
		this.commits = Commits.of(git);
	}

	public static GitMerge from(Repository git) {
		return new GitMerge(git);
	}

	public GitMerge into(IDatabase database) {
		this.database = database;
		return this;
	}

	public GitMerge update(ObjectIdStore workspaceIds) {
		this.workspaceIds = workspaceIds;
		return this;
	}

	public GitMerge as(PersonIdent committer) {
		this.committer = committer;
		return this;
	}

	public GitMerge resolveConflictsWith(ConflictResolver conflictResolver) {
		this.conflictResolver = conflictResolver;
		return this;
	}

	public GitMerge resolveLibrariesWith(LibraryResolver libraryResolver) {
		this.libraryResolver = libraryResolver;
		return this;
	}

	GitMerge applyStash() {
		this.applyStash = true;
		return this;
	}

	@Override
	public Boolean run() throws IOException, GitAPIException {
		if (git == null || database == null)
			throw new IllegalStateException("Git repository and database must be set");
		var behind = history.getBehind(getRef());
		if (behind.isEmpty())
			return false;
		var localCommit = commits.get(commits.resolve(Constants.LOCAL_BRANCH));
		var remoteCommit = getRemoteCommit();
		if (remoteCommit == null)
			return false;
		var toMount = resolveLibraries(remoteCommit);
		if (toMount == null)
			return null;
		var commonParent = history.commonParentOf(Constants.LOCAL_REF, getRef());
		var diffs = Diffs.between(git, commonParent, remoteCommit);
		var deleted = diffs.stream()
				.filter(d -> d.diffType == DiffType.DELETED)
				.map(d -> d.toReference(Side.OLD))
				.collect(Collectors.toList());
		var addedOrChanged = diffs.stream()
				.filter(d -> d.diffType != DiffType.DELETED)
				.map(d -> d.toReference(Side.NEW))
				.collect(Collectors.toList());
		var ahead = !applyStash
				? history.getAhead()
				: new ArrayList<>();
		if (progressMonitor != null) {
			var work = toMount.size() + addedOrChanged.size() + deleted.size() + (!ahead.isEmpty() ? 1 : 0);
			progressMonitor.beginTask("Merging data", work);
		}
		mountLibraries(toMount);
		var gitStore = new GitStoreReader(git, localCommit, remoteCommit, addedOrChanged, conflictResolver);
		var importHelper = new ImportHelper(git, database, workspaceIds, progressMonitor);
		importHelper.conflictResolver = conflictResolver;
		importHelper.runImport(gitStore);
		importHelper.delete(deleted);
		var result = new ImportResult(gitStore, deleted);
		String commitId = remoteCommit.id;
		if (!applyStash) {
			if (ahead.isEmpty()) {
				updateHead(remoteCommit);
			} else {
				commitId = createMergeCommit(localCommit, remoteCommit, result);
			}
		}
		importHelper.updateWorkspaceIds(commitId, result, applyStash);
		return result.count() > 0;
	}

	private Commit getRemoteCommit() throws GitAPIException {
		if (!applyStash)
			return commits.get(commits.resolve(Constants.REMOTE_BRANCH));
		var commits = Git.wrap(git).stashList().call();
		if (commits == null || commits.isEmpty())
			return null;
		return new Commit(commits.iterator().next());
	}

	private String getRef() {
		return applyStash ? org.eclipse.jgit.lib.Constants.R_STASH : Constants.REMOTE_REF;
	}

	private String createMergeCommit(Commit localCommit, Commit remoteCommit, ImportResult result)
			throws IOException {
		var config = new GitConfig(database, workspaceIds, git);
		var diffs = result.merged().stream()
				.map(r -> new Change(DiffType.MODIFIED, r))
				.collect(Collectors.toList());
		result.keepDeleted().forEach(r -> diffs.add(new Change(DiffType.DELETED, r)));
		result.deleted().forEach(r -> {
			if (conflictResolver != null
					&& conflictResolver.isConflict(r)
					&& conflictResolver.resolveConflict(r, null).type == ConflictResolutionType.OVERWRITE) {
				diffs.add(new Change(DiffType.DELETED, r));
			}
		});
		if (progressMonitor != null) {
			progressMonitor.subTask("Writing merged changes");
		}
		var commitWriter = new CommitWriter(config, committer);
		var mergeMessage = "Merge remote-tracking branch";
		var commitId = commitWriter.mergeCommit(mergeMessage, diffs, localCommit.id, remoteCommit.id);
		if (progressMonitor != null) {
			progressMonitor.worked(1);
		}
		return commitId;
	}

	private void updateHead(Commit commit) throws IOException {
		var update = git.updateRef(Constants.LOCAL_BRANCH);
		update.setNewObjectId(ObjectId.fromString(commit.id));
		update.update();
	}

	private List<Library> resolveLibraries(Commit commit) {
		var info = Repositories.infoOf(git, commit);
		if (info == null)
			return new ArrayList<>();
		if (libraryResolver == null)
			return null;
		var remoteLibs = info.libraries();
		var localLibs = database.getLibraries();
		var libs = new ArrayList<Library>();
		for (var newLib : remoteLibs) {
			if (localLibs.contains(newLib))
				continue;
			var lib = libraryResolver.resolve(newLib);
			if (lib == null)
				return null;
			libs.add(lib);
		}
		return libs;
	}

	private boolean mountLibraries(List<Library> newLibraries)
			throws IOException {
		for (var newLib : newLibraries) {
			if (progressMonitor != null) {
				progressMonitor.subTask("Mounting library " + newLib.id());
			}
			newLib.mountTo(database);
			if (progressMonitor != null) {
				progressMonitor.worked(1);
			}
		}
		return true;
		// TODO remove libs that are not anymore in package info
	}

}
