package com.naga.ideaplugins.CheckGitCRLFChanges;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RevertCRLFOnlyChangesAction extends AnAction {

    private static final Logger log = LoggerFactory.getLogger(RevertCRLFOnlyChangesAction.class);

    //Allowed folders to check for CRLF changes
    private static final String ignorePathRegexes = ".*\\.idea.*|.*\\.git.*|.*build.*|.*target.*|.*node_modules.*|.*dist.*|.*out.*|.*\\.gradle.*|.*\\.vscode.*|.*\\.settings.*|.*\\.classpath.*|.*\\.project.*|.*\\.iml.*|.*\\.ipr.*|.*\\.iws.*|.*\\.metadata.*|.*\\.history.*|.*\\.cache.*|.*\\.svn.*|.*\\.hg.*|.*\\.bzr.*|.*\\.DS_Store.*|.*\\.gitignore.*|.*\\.gitattributes.*|.*\\.gitmodules.*|.*\\.gitkeep";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }


        VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (virtualFiles == null) {
            log.info("No files selected");
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing Files for CRLF Changes", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);

                for (int i = 0; i < virtualFiles.length; i++) {
                    VirtualFile file = virtualFiles[i];
                    indicator.setFraction((double) i / virtualFiles.length);
                    indicator.setText("Processing file " + (i + 1) + " of " + virtualFiles.length);

                    if (file.getPath().matches(ignorePathRegexes)) {
                        log.info("Ignoring file: " + file.getPath());
                        continue;
                    }

                    GitRepository repository = gitRepositoryManager.getRepositoryForFile(file);
                    if (repository == null) {
                        log.warn("No git repository found for file: " + file.getPath());
                        continue;
                    }

                    try {
                        if (isCRLFOnlyChanges(repository, project, file)) {
                            revertFileToGitVersion(repository, project, file);
                        }
                    } catch (IOException | VcsException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });
    }

    private void revertFileToGitVersion(GitRepository repository, Project project, VirtualFile file) {
        try {
            rollbackFileToGitVersion(repository, project, file);
            VcsDirtyScopeManager.getInstance(project).fileDirty(file);
        } catch (Exception ex) {
            log.error("Error reverting file to git version", ex);
        }
    }

    private boolean isCRLFOnlyChanges(GitRepository repository, Project project, VirtualFile file) throws VcsException, IOException {

        String previousText = getPreviousText(project, repository, getGitFilePath(repository, file.getPath()));
        if (previousText == null) {
            return false;
        }

        try {
            byte[] content = file.contentsToByteArray();
            String currentText = new String(content);

            String normalizedCurrentText = currentText.replaceAll("\r\n", "\n").replaceAll("\n", "");
            String normalizedPreviousText = previousText.replaceAll("\r\n", "\n").replaceAll("\n", "");

            // Check if the only changes are CRLF changes
            return normalizedCurrentText.equals(normalizedPreviousText);
        } catch (IOException ex) {
            log.error("Error reading file content", ex);
            return false;
        }
    }

    private void rollbackFileToGitVersion(GitRepository repository, Project project, VirtualFile file) {
        Git git = Git.getInstance();
        try {
            GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.CHECKOUT);
            handler.addParameters("HEAD", "--", getGitFilePath(repository, file.getPath()));
            GitCommandResult result = git.runCommand(handler);
            if (result.success()) {
                VcsDirtyScopeManager.getInstance(project).fileDirty(file);
            } else {
                log.error("Error rolling back file to git version: " + result.getErrorOutputAsJoinedString());
            }
        } catch (Exception ex) {
            log.error("Error rolling back file to git version", ex);
        }
    }

    /**
     * Get the text of the file in the previous commit - null if the file is not in the repository root or any error occurs while reading the file
     */
    private String getPreviousText(Project project, GitRepository repository, String relativePath) {
        VirtualFile root = repository.getRoot();
        try {
            byte @NotNull [] content = GitFileUtils.getFileContent(project, root, "HEAD", relativePath);
            return new String(content);
        } catch (VcsException e) {
            if (e.getMessage().contains("exists on disk, but not in 'HEAD'")) {
                log.warn("File " + relativePath + " does not exist in HEAD");
            } else {
                log.error("Error reading file content", e);
            }
            return null;

        }
    }

    private static @NotNull String getGitFilePath(GitRepository repository, String filePath) {
        VirtualFile root = repository.getRoot();
        return filePath.substring(root.getPath().length() + 1);
    }
}
