package org.openlca.git.actions;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.openlca.core.database.IDatabase;
import org.openlca.git.GitConfig;
import org.openlca.git.ObjectIdStore;
import org.openlca.git.find.Commits;
import org.openlca.git.model.Diff;
import org.openlca.git.util.DiffEntries;
import org.openlca.git.writer.CommitWriter;
import org.openlca.util.Strings;

public class GitCommit {

	private final IDatabase database;
	private FileRepository git;
	private Commits commits;
	private List<Diff> diffs;
	private String message;
	private ObjectIdStore workspaceIds;
	private PersonIdent committer;

	private GitCommit(IDatabase database) {
		this.database = database;
	}

	public static GitCommit from(IDatabase database) {
		return new GitCommit(database);
	}

	public GitCommit to(FileRepository git) {
		this.git = git;
		this.commits = Commits.of(git);
		return this;
	}

	public GitCommit diffs(List<Diff> diffs) {
		this.diffs = diffs;
		return this;
	}

	public GitCommit as(PersonIdent committer) {
		this.committer = committer;
		return this;
	}

	public GitCommit withMessage(String message) {
		this.message = message;
		return this;
	}

	public GitCommit update(ObjectIdStore workspaceIds) {
		this.workspaceIds = workspaceIds;
		return this;
	}

	public String run() throws IOException {
		if (git == null || database == null || Strings.nullOrEmpty(message))
			throw new IllegalStateException("Git repository, database and message must be set");
		var config = new GitConfig(database, workspaceIds, git, committer);
		if (diffs == null) {
			if (workspaceIds == null)
				throw new IllegalStateException("ObjectIdStore must be set when no diffs are specified");
			diffs = getWorkspaceDiffs(config);
		}
		var writer = new CommitWriter(config);
		return writer.commit(message, diffs);
	}

	private List<Diff> getWorkspaceDiffs(GitConfig config) throws IOException {
		var commit = commits.head();
		var leftCommitId = commit != null ? commit.id : null;
		return DiffEntries.workspace(config, commit).stream()
				.map(e -> new Diff(e, leftCommitId, null))
				.toList();
	}
}
