package com.cloudbees.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jvnet.localizer.Localizable;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitComment;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static hudson.model.Result.*;

/**
 * Create commit status notifications on the commits based on the outcome of the build.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class GitHubCommitNotifier extends Notifier {


    @DataBoundConstructor
    public GitHubCommitNotifier() {
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        BuildData buildData = build.getAction(BuildData.class);
        String sha1 = ObjectId.toString(buildData.getLastBuiltRevision().getSha1());

        for (GitHubRepositoryName name : GitHubRepositoryNameContributor.parseAssociatedNames(build.getProject())) {
            for (GHRepository repository : name.resolve()) {
                GHCommitState state;
                String msg;

                // We do not use `build.getDurationString()` because it appends 'and counting' (build is still running)
                final String duration = Util.getTimeSpanString(System.currentTimeMillis() - build.getTimeInMillis());

                Result result = build.getResult();
                if (result.isBetterOrEqualTo(SUCCESS)) {
                    state = GHCommitState.SUCCESS;
                    msg = Messages.CommitNotifier_Success(build.getDisplayName(), duration);
                } else if (result.isBetterOrEqualTo(UNSTABLE)) {
                    state = GHCommitState.FAILURE;
                    msg = Messages.CommitNotifier_Unstable(build.getDisplayName(), duration);
                } else {
                    state = GHCommitState.ERROR;
                    msg = Messages.CommitNotifier_Failed(build.getDisplayName(), duration);
                }
                GHCommit commit = repository.getCommit(sha1);
                GHCommitComment comment = commit.createComment(duration);
                LOGGER.info("Attempted to create comment: " + comment);
                listener.getLogger().println(Messages.GitHubCommitNotifier_SettingCommitStatus(repository.getUrl() + "/commit/" + sha1));
                repository.createCommitStatus(sha1, state, build.getAbsoluteUrl(), msg);
            }
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Set build status on GitHub commit";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GitHubPushTrigger.class.getName());

}
