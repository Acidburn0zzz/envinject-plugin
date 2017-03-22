package org.jenkinsci.plugins.envinject;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSON;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.envinject.model.EnvInjectJobPropertyContributor;
import org.jenkinsci.plugins.envinject.model.EnvInjectJobPropertyContributorDescriptor;
import org.jenkinsci.plugins.envinject.service.EnvInjectContributorManagement;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author Gregory Boissinot
 */
public class EnvInjectJobProperty<T extends Job<?, ?>> extends JobProperty<T> {

    @CheckForNull
    private EnvInjectJobPropertyInfo info = new EnvInjectJobPropertyInfo();
    private boolean on;
    private boolean keepJenkinsSystemVariables;
    private boolean keepBuildVariables;
    private boolean overrideBuildParameters;
    @CheckForNull
    @GuardedBy("this")
    private EnvInjectJobPropertyContributor[] contributors;
    @CheckForNull
    @GuardedBy("this")
    private transient EnvInjectJobPropertyContributor[] contributorsComputed;

    @DataBoundConstructor
    public EnvInjectJobProperty(EnvInjectJobPropertyInfo info) {
        this.info = info;
    }

    @Restricted(NoExternalUse.class)
    @Deprecated
    public EnvInjectJobProperty() {
    }

    @CheckForNull
    @SuppressWarnings("unused")
    public EnvInjectJobPropertyInfo getInfo() {
        return info;
    }

    @SuppressWarnings("unused")
    public boolean isOn() {
        return on;
    }

    @SuppressWarnings("unused")
    public boolean isKeepJenkinsSystemVariables() {
        return keepJenkinsSystemVariables;
    }

    @SuppressWarnings("unused")
    public boolean isKeepBuildVariables() {
        return keepBuildVariables;
    }

    @SuppressWarnings("unused")
    public boolean isOverrideBuildParameters() {
        return overrideBuildParameters;
    }

    @Nonnull
    @SuppressWarnings("unused")
    public synchronized EnvInjectJobPropertyContributor[] getContributors() {
        if (contributorsComputed == null) {
            try {
                contributorsComputed = computeEnvInjectContributors();
            } catch (org.jenkinsci.lib.envinject.EnvInjectException e) {
                e.printStackTrace();
            }
            contributors = contributorsComputed;
        }

        return contributors != null ? Arrays.copyOf(contributors, contributors.length) : new EnvInjectJobPropertyContributor[0];
    }

    @Nonnull
    private synchronized EnvInjectJobPropertyContributor[] computeEnvInjectContributors() throws org.jenkinsci.lib.envinject.EnvInjectException {

        DescriptorExtensionList<EnvInjectJobPropertyContributor, EnvInjectJobPropertyContributorDescriptor>
                descriptors = EnvInjectJobPropertyContributor.all();

        // If the config are loaded with success (this step) and the descriptors size doesn't have change
        // we considerate, they are the same, therefore we retrieve instances
        if (contributors != null && contributors.length == descriptors.size()) {
            return contributors;
        }

        EnvInjectContributorManagement envInjectContributorManagement = new EnvInjectContributorManagement();
        EnvInjectJobPropertyContributor[] contributorsInstance = envInjectContributorManagement.getNewContributorsInstance();

        //No jobProperty Contributors ==> new configuration
        if (contributors == null || contributors.length == 0) {
            return contributorsInstance;
        }

        List<EnvInjectJobPropertyContributor> result = new ArrayList<EnvInjectJobPropertyContributor>();
        for (EnvInjectJobPropertyContributor contributor1 : contributorsInstance) {
            for (EnvInjectJobPropertyContributor contributor2 : contributors) {
                if (contributor1.getDescriptor().getClass() == contributor2.getDescriptor().getClass()) {
                    result.add(contributor2);
                } else {
                    result.add(contributor1);
                }
            }
        }
        return result.toArray(new EnvInjectJobPropertyContributor[result.size()]);
    }

    /**
     * @deprecated Use constructor with parameter
     */
    @Deprecated
    public void setInfo(@CheckForNull EnvInjectJobPropertyInfo info) {
        this.info = info;
    }

    @DataBoundSetter
    public void setOn(boolean on) {
        this.on = on;
    }

    @DataBoundSetter
    public void setKeepJenkinsSystemVariables(boolean keepJenkinsSystemVariables) {
        this.keepJenkinsSystemVariables = keepJenkinsSystemVariables;
    }

    @DataBoundSetter
    public void setKeepBuildVariables(boolean keepBuildVariables) {
        this.keepBuildVariables = keepBuildVariables;
    }

    @DataBoundSetter
    public void setOverrideBuildParameters(boolean overrideBuildParameters) {
        this.overrideBuildParameters = overrideBuildParameters;
    }

    @DataBoundSetter
    public synchronized void setContributors(EnvInjectJobPropertyContributor[] jobPropertyContributors) {
        this.contributors = jobPropertyContributors;
    }

    @Override
    public JobProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        EnvInjectJobProperty property = (EnvInjectJobProperty) super.reconfigure(req, form);
        if (property != null && property.info != null && !Jenkins.getActiveInstance().hasPermission(Jenkins.RUN_SCRIPTS)) {
            // Don't let non RUN_SCRIPT users set arbitrary groovy script
            property.info = new EnvInjectJobPropertyInfo(property.info.propertiesFilePath, property.info.propertiesContent,
                                                         property.info.getScriptFilePath(), property.info.getScriptContent(),
                                                         this.info != null ? this.info.getGroovyScriptContent() : "",
                                                         property.info.isLoadFilesFromMaster());
        }
        return property;
    }

    @Extension
    @SuppressWarnings("unused")
    public static final class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "[Environment Inject] -" + Messages.envinject_set_displayName();
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public String getHelpFile() {
            return "/plugin/envinject/help.html";
        }

        @Override
        public EnvInjectJobProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.optBoolean("on")) {
                return (EnvInjectJobProperty)super.newInstance(req, formData);
            }
            return null;
        }

        @Nonnull 
        public DescriptorExtensionList<EnvInjectJobPropertyContributor, EnvInjectJobPropertyContributorDescriptor> getEnvInjectContributors() {
            return EnvInjectJobPropertyContributor.all();
        }

        @CheckForNull 
        public EnvInjectJobPropertyContributor[] getContributorsInstance() {
            EnvInjectContributorManagement envInjectContributorManagement = new EnvInjectContributorManagement();
            try {
                return envInjectContributorManagement.getNewContributorsInstance();
            } catch (org.jenkinsci.lib.envinject.EnvInjectException e) {
                e.printStackTrace();
            }
            return null;
        }

        public boolean isEnvInjectContributionActivated() {
            EnvInjectContributorManagement envInjectContributorManagement = new EnvInjectContributorManagement();
            return envInjectContributorManagement.isEnvInjectContributionActivated();
        }

    }


    @Deprecated
    private transient boolean injectGlobalPasswords;
    @Deprecated
    private transient EnvInjectPasswordEntry[] passwordEntries;
    @Deprecated
    private transient boolean keepSystemVariables;

    @Deprecated
    public boolean isInjectGlobalPasswords() {
        return injectGlobalPasswords;
    }

    @Deprecated
    @CheckForNull
    public EnvInjectPasswordEntry[] getPasswordEntries() {
        return passwordEntries;
    }
}
