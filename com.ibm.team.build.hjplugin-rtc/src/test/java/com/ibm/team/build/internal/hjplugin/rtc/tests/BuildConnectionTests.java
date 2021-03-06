/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.common.ScmConstants;
import com.ibm.team.build.common.model.BuildState;
import com.ibm.team.build.common.model.BuildStatus;
import com.ibm.team.build.common.model.IBuildActivity;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultContribution;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.client.workitem.WorkItemHelper;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.common.links.BuildLinkTypes;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConfiguration;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConnection;
import com.ibm.team.build.internal.hjplugin.rtc.IBuildResultInfo;
import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.scm.BuildWorkspaceDescriptor;
import com.ibm.team.links.client.ILinkManager;
import com.ibm.team.links.common.IItemReference;
import com.ibm.team.links.common.ILink;
import com.ibm.team.links.common.ILinkCollection;
import com.ibm.team.links.common.ILinkQueryPage;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.factory.IReferenceFactory;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;

@SuppressWarnings({ "nls", "restriction" })
public class BuildConnectionTests {
	
	private final static List<String> TERMINATE_PROPERTIES = Arrays.asList(new String[] {
			IBuildResult.PROPERTY_BUILD_STATE,
			IBuildResult.PROPERTY_BUILD_STATUS});

	private RepositoryConnection connection;

	public BuildConnectionTests(RepositoryConnection repositoryConnection) {
		this.connection = repositoryConnection;
	}

	public void verifyBuildResultContributions(Map<String, String> artifacts) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		ITeamBuildClient buildClient = (ITeamBuildClient) repo.getClientLibrary(ITeamBuildClient.class);
		
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(artifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID)), null);
		IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchCompleteItem(buildResultHandle, IItemManager.REFRESH, null);
		
		AssertUtil.assertEquals(artifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ITEM_ID), buildResult.getBuildDefinition().getItemId().getUuidValue());

        verifyBuildWorkspaceContribution(repo, buildClient, buildResult,
				artifacts);
        
        verifySnapshotContribution(repo, buildClient, buildResult, artifacts);
        verifyBuildActivity(repo, buildClient, buildResult);
        verifyWorkItemPublished(repo, buildClient, buildResult, 
        		artifacts.get("cs3wi1itemId"),
        		artifacts.get("cs4wi1itemId"),
        		artifacts.get("cs4wi2itemId"),
        		artifacts.get("cs4wi3itemId"),
        		artifacts.get("cs4wi4itemId"),
        		artifacts.get("cs4wi5itemId"));

	}

	private void verifyWorkItemPublished(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult,
			String... workItemIds) throws Exception {

        Set<String> workItems = new HashSet<String>();
        for (String id : workItemIds) {
        	workItems.add(id);
        }
        
        IWorkItemHandle[] fixedWorkItems = WorkItemHelper.getFixedInBuild(repo, buildResult, null);
        AssertUtil.assertEquals(workItems.size(), fixedWorkItems.length);

        for (IWorkItemHandle workItemHandle : fixedWorkItems) {
        	if (!workItems.contains(workItemHandle.getItemId().getUuidValue())) {
            	IWorkItem workItem = (IWorkItem) repo.itemManager().fetchCompleteItem(workItemHandle, IItemManager.REFRESH, null);
            	AssertUtil.fail("Unexpected work item " + workItem.getId() + " marked as fixed by build");
        	}
        	verifyWorkItemLinkToBuildResult(repo, workItemHandle, buildResult);
        }
	}

    private void verifyWorkItemLinkToBuildResult(ITeamRepository repo, IWorkItemHandle workItemHandle, IBuildResult buildResult)
            throws TeamRepositoryException {

        ILinkManager linkManager = (ILinkManager) repo.getClientLibrary(ILinkManager.class);

        ILinkQueryPage results = linkManager.findLinksByTarget(BuildLinkTypes.INCLUDED_WORK_ITEMS,
                IReferenceFactory.INSTANCE.createReferenceToItem(workItemHandle), null);

        ILinkCollection linkCollection = results.getAllLinksFromHereOn();

        boolean found = false;
        for (Iterator<ILink> i = linkCollection.iterator(); i.hasNext(); ) {
        	ILink link = i.next();
            IReference reference = link.getSourceRef();
            AssertUtil.assertTrue(reference.isItemReference(), "reference " + reference.getComment() + " is not an item reference");

            IItemHandle referencedItem = ((IItemReference) reference).getReferencedItem();
            AssertUtil.assertTrue(referencedItem instanceof IBuildResultHandle, "referencedItem is a " + referencedItem.getClass().getName() + " not an IBuildResultHandle");

            if (buildResult.getItemId().getUuidValue().equals(referencedItem.getItemId().getUuidValue())) {
            	found = true;
            	break;
            }
        }
        if (!found) {
        	IWorkItem workItem = (IWorkItem) repo.itemManager().fetchCompleteItem(workItemHandle, IItemManager.REFRESH, null);
        	AssertUtil.fail("Work item " + workItem.getId() + " is missing link to build result");
        }
    }

	private void verifyBuildActivity(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult) throws Exception {
		IBuildActivity[] activities = buildClient.getBuildActivities(buildResult, null);
		AssertUtil.assertEquals(1, activities.length);
		IBuildActivity activity = activities[0];
		AssertUtil.assertEquals("Jazz Source Control setup", activity.getLabel());
		AssertUtil.assertTrue(activity.isComplete(), "activity is not complete");
		AssertUtil.assertEquals(2, activity.getChildActivities().length);
		AssertUtil.assertEquals("Accepting changes", activity.getChildActivities()[0].getLabel());
		AssertUtil.assertEquals("Fetching files", activity.getChildActivities()[1].getLabel());
	}

	private void verifySnapshotContribution(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult,
			Map<String, String> artifacts) throws TeamRepositoryException {
		// Verify the snapshot contribution was created
		IBaselineSetHandle baselineSetHandle = (IBaselineSetHandle) IBaselineSet.ITEM_TYPE.createItemHandle(UUID.valueOf(artifacts.get(TestSetupTearDownUtil.ARTIFACT_BASELINE_SET_ITEM_ID)), null);
        IBaselineSet baselineSet = (IBaselineSet) repo.itemManager().fetchCompleteItem(baselineSetHandle, IItemManager.REFRESH, null);
        IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(buildResult,
                ScmConstants.EXTENDED_DATA_TYPE_ID_BUILD_SNAPSHOT, null);

        AssertUtil.assertEquals(1, contributions.length);

        AssertUtil.assertEquals(NLS.bind("snapshot {0}", baselineSet.getName()), //$NON-NLS-1$
                contributions[0].getLabel());
        AssertUtil.assertFalse((contributions[0].isImpactsPrimaryResult()), "Snapshot contribution impacts primary result");
	}

	private void verifyBuildWorkspaceContribution(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult,
			Map<String, String> artifacts) throws TeamRepositoryException {
		// Verify the build result workspace contribution was created.
		IWorkspaceHandle workspaceHandle = (IWorkspaceHandle) IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(artifacts.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID)), null);
        IWorkspace workspace = (IWorkspace) repo.itemManager().fetchCompleteItem(workspaceHandle, IItemManager.REFRESH, null);
        IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(buildResult,
                ScmConstants.EXTENDED_DATA_TYPE_ID_BUILD_WORKSPACE, null);
        AssertUtil.assertEquals(1, contributions.length);

        AssertUtil.assertEquals(workspace.getName(), contributions[0].getLabel());
        AssertUtil.assertFalse((contributions[0].isImpactsPrimaryResult()), "Workspace contribution impacts primary result");

        AssertUtil.assertEquals(workspace.getItemId(),
                contributions[0].getExtendedContribution().getItemId());
	}

	public void testCreateBuildResult(String testName) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			final Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = getListener(failure);
			
			// create 2 workspaces, a build one and a personal build one
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			IWorkspaceConnection personalWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "2");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, personalWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			
			// test the straight forward build result creation
			String buildResultItemId = connection.createBuildResult(testName, null, "my buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			if (failure[0] != null) {
				throw failure[0];
			}

			BuildConfiguration buildConfiguration = new BuildConfiguration(repo, "");
			buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
			if (failure[0] != null) {
				throw failure[0];
			}

			AssertUtil.assertFalse(buildConfiguration.isPersonalBuild(), "Should NOT be a personal build");
			BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
			AssertUtil.assertEquals(buildWorkspace.getContextHandle().getItemId(), workspaceDescriptor.getWorkspaceHandle().getItemId());
			AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
			
			artifactIds.remove(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
			BuildUtil.deleteBuildResult(repo, buildResultItemId);

			// test the creation of a personal build
			buildResultItemId = connection.createBuildResult(testName, personalWorkspace.getResolvedWorkspace().getName(), "my personal buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			if (failure[0] != null) {
				throw failure[0];
			}
			
			buildConfiguration = new BuildConfiguration(repo, "");
			buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
			if (failure[0] != null) {
				throw failure[0];
			}
			
			AssertUtil.assertTrue(buildConfiguration.isPersonalBuild(), "Should be a personal build");
			workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
			AssertUtil.assertEquals(personalWorkspace.getContextHandle().getItemId(), workspaceDescriptor.getWorkspaceHandle().getItemId());
			AssertUtil.assertEquals("my personal buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}

	public void testCreateBuildResultFail(String testName) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			final Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = getListener(failure);
			
			// create a build workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			// no build engine for the build definition
			BuildUtil.createBuildDefinition(repo, testName, false, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			
			// build result creation should fail
			try {
				String buildResultItemId = connection.createBuildResult(testName, null, "my buildLabel", listener, null, Locale.getDefault());
				artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
				if (failure[0] != null) {
					throw failure[0];
				}
				AssertUtil.fail("Without a build engine, the result should not be able to be created");
			} catch (Exception e) {
				// expected
				AssertUtil.assertTrue(e instanceof RTCConfigurationException, "Unexpected exception encountered " + e.getMessage());
			}
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}

	public void testExternalLinks(String testName) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			final Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = getListener(failure);
			
			// create build workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			
			// create a build result
			String buildResultItemId = connection.createBuildResult(testName, null, "external links test 1", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (failure[0] != null) {
				throw failure[0];
			}

			// Add external links
			connection.getBuildConnection().createBuildLinks(buildResultItemId, "http://localHost:8080", "myJob", "myJob/2",
					listener, null);
			if (failure[0] != null) {
				throw failure[0];
			}
			
			// verify the links are on the build result
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			ITeamBuildClient buildClient = (ITeamBuildClient) repo.getClientLibrary(ITeamBuildClient.class);
			IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(buildResultHandle, IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, null);
			AssertUtil.assertEquals(2, contributions.length);
			for (IBuildResultContribution contribution : contributions) {
				AssertUtil.assertEquals(IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, contribution.getExtendedContributionTypeId());
				if (contribution.getLabel().equals("Hudson/Jenkins Job")) {
					AssertUtil.assertEquals("http://localHost:8080/myJob", contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else if (contribution.getLabel().equals("Hudson/Jenkins Build")) {
					AssertUtil.assertEquals("http://localHost:8080/myJob/2", contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else {
					AssertUtil.fail("Unexpected contribution " + contribution.getLabel());
				}
			}
			
			// create another build result
			BuildUtil.deleteBuildResult(repo, buildResultItemId);
			buildResultItemId = connection.createBuildResult(testName, null, "external links test 2", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (failure[0] != null) {
				throw failure[0];
			}

			// test creating links when we could not get the hudson url
			connection.getBuildConnection().createBuildLinks(buildResultItemId, null, "anotherJob", "anotherJob/44", listener, null);
			if (failure[0] != null) {
				throw failure[0];
			}

			// verify the links are on the build result
			buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			contributions = buildClient.getBuildResultContributions(buildResultHandle, IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, null);
			AssertUtil.assertEquals(2, contributions.length);
			for (IBuildResultContribution contribution : contributions) {
				AssertUtil.assertEquals(IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, contribution.getExtendedContributionTypeId());
				if (contribution.getLabel().equals("Hudson/Jenkins Job")) {
					AssertUtil.assertEquals("http://junit.ottawa.ibm.com:8081/anotherJob", contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else if (contribution.getLabel().equals("Hudson/Jenkins Build")) {
					AssertUtil.assertEquals("http://junit.ottawa.ibm.com:8081/anotherJob/44", contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else {
					AssertUtil.fail("Unexpected contribution " + contribution.getLabel());
				}
			}
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}

	public void testBuildTermination(String testName) throws Exception {
		// Test start & terminate build (status ok)
		// Test start & terminate build (cancelled)
		// Test start & terminate build (failed)
		// Test start & terminate build (unstable)
		// Test start & (set status of build in test to warning) & terminate build (status ok)
		// Test start & (set status of build in test to error) & terminate build (status unstable)
		// Test start & (set status of build in test to error) & terminate build (abandon)
		// Test start & (abandon build in test) & terminate build (status ok)
		// Test start & (abandon build in test) & terminate build (cancelled)
		// Test start & (abandon build in test) & terminate build (failed)
		// Test start & (abandon build in test) & terminate build (unstable)
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			// create a build workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			
			// start & terminate build (status ok)
			startTerminateAndVerify(repo, testName, false, BuildConnection.OK, BuildState.COMPLETED, BuildStatus.OK, artifactIds);

			// start & terminate build (cancelled)
			startTerminateAndVerify(repo, testName, true, BuildConnection.OK, BuildState.INCOMPLETE, BuildStatus.OK, artifactIds);
			
			// start & terminate build (failed)
			startTerminateAndVerify(repo, testName, false, BuildConnection.ERROR, BuildState.COMPLETED, BuildStatus.ERROR, artifactIds);

			// start & terminate build (unstable)
			startTerminateAndVerify(repo, testName, false, BuildConnection.UNSTABLE, BuildState.COMPLETED, BuildStatus.WARNING, artifactIds);

			// Test start & (set status of build in test to warning) & terminate build (status ok)
			String buildResultItemId = startBuild(repo, testName, artifactIds);
			setBuildStatus(repo, buildResultItemId, false, BuildStatus.WARNING);
			terminateAndVerify(repo, false, BuildConnection.OK, BuildState.COMPLETED, BuildStatus.WARNING, artifactIds);
			
			// Test start & (set status of build in test to error) & terminate build (status unstable)
			buildResultItemId = startBuild(repo, testName, artifactIds);
			setBuildStatus(repo, buildResultItemId, false, BuildStatus.ERROR);
			terminateAndVerify(repo, false, BuildConnection.UNSTABLE, BuildState.COMPLETED, BuildStatus.ERROR, artifactIds);
			
			// Test start & (set status of build in test to error) & terminate build (abandon)
			buildResultItemId = startBuild(repo, testName, artifactIds);
			setBuildStatus(repo, buildResultItemId, false, BuildStatus.ERROR);
			terminateAndVerify(repo, true, BuildConnection.OK, BuildState.INCOMPLETE, BuildStatus.ERROR, artifactIds);

			// Test start & (abandon build in test) & terminate build (status ok)
			buildResultItemId = startBuild(repo, testName, artifactIds);
			setBuildStatus(repo, buildResultItemId, true, BuildStatus.ERROR);
			terminateAndVerify(repo, true, BuildConnection.OK, BuildState.INCOMPLETE, BuildStatus.ERROR, artifactIds);

			// Test start & (abandon build in test) & terminate build (cancelled)
			buildResultItemId = startBuild(repo, testName, artifactIds);
			setBuildStatus(repo, buildResultItemId, true, BuildStatus.ERROR);
			terminateAndVerify(repo, true, BuildConnection.OK, BuildState.INCOMPLETE, BuildStatus.ERROR, artifactIds);

			// Test start & (abandon build in test) & terminate build (failed)
			buildResultItemId = startBuild(repo, testName, artifactIds);
			setBuildStatus(repo, buildResultItemId, true, BuildStatus.OK);
			terminateAndVerify(repo, true, BuildConnection.OK, BuildState.INCOMPLETE, BuildStatus.OK, artifactIds);

			// Test start & (abandon build in test) & terminate build (unstable)
			buildResultItemId = startBuild(repo, testName, artifactIds);
			setBuildStatus(repo, buildResultItemId, true, BuildStatus.WARNING);
			terminateAndVerify(repo, true, BuildConnection.OK, BuildState.INCOMPLETE, BuildStatus.WARNING, artifactIds);
			
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}

	private void setBuildStatus(ITeamRepository repo, String buildResultItemId,
			boolean abandon, BuildStatus status) throws Exception {
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
		buildResult = (IBuildResult) buildResult.getWorkingCopy();
		buildResult.setStatus(status);
		ClientFactory.getTeamBuildClient(repo).save(buildResult, new NullProgressMonitor());
		if (abandon) {
			ClientFactory.getTeamBuildRequestClient(repo).makeBuildIncomplete(buildResultHandle, new String[] {
					IBuildResult.PROPERTY_BUILD_STATE,
					IBuildResult.PROPERTY_BUILD_STATUS}, null);
		}
	}

	private void startTerminateAndVerify(ITeamRepository repo, String testName, boolean aborted, int buildState,
			BuildState expectedState, BuildStatus expectedStatus,
			Map<String, String> artifactIds)
			throws Exception, TeamRepositoryException {
		startBuild(repo, testName, artifactIds);

		// terminate
		terminateAndVerify(repo, aborted, buildState, expectedState,
				expectedStatus, artifactIds);
	}

	private String startBuild(ITeamRepository repo, String testName,
			Map<String, String> artifactIds) throws Exception,
			TeamRepositoryException {
		final Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = getListener(failure);

		String buildResultItemId = connection.createBuildResult(testName, null, "my buildLabel", listener, null, Locale.getDefault());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
		if (failure[0] != null) {
			throw failure[0];
		}
		
		// make sure the build is in progress
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
		AssertUtil.assertEquals(BuildState.IN_PROGRESS, buildResult.getState());
		return buildResultItemId;
	}

	private void terminateAndVerify(ITeamRepository repo, boolean aborted,
			int buildState, BuildState expectedState,
			BuildStatus expectedStatus, Map<String, String> artifactIds)
					throws Exception, TeamRepositoryException {
		final Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = getListener(failure);

		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		connection.terminateBuild(buildResultItemId, aborted, buildState, listener, null);
		if (failure[0] != null) {
			throw failure[0];
		}

		// verify
		IBuildResultHandle buildResultHandle;
		IBuildResult buildResult;
		buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
		AssertUtil.assertEquals(expectedState, buildResult.getState());
		AssertUtil.assertEquals(expectedStatus, buildResult.getStatus());
		
		// delete the build result artifact
		BuildUtil.deleteBuildResult(repo, buildResultItemId);
		artifactIds.remove(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
	}

	private IConsoleOutput getListener(final Exception[] failure) {
		IConsoleOutput listener = new IConsoleOutput() {
			
			@Override
			public void log(String message, Exception e) {
				failure[0] = e;
			}
			
			@Override
			public void log(String message) {
				// not good
				throw new AssertionFailedException(message);
			}
		};
		return listener;
	}
	
	public String testBuildResultInfo(String testName, final IBuildResultInfo buildResultInfo) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			final Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = getListener(failure);
			
			// create 2 workspaces, a build one and a personal build one
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			IWorkspaceConnection personalWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "2");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, personalWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");

			// test a personal build
			final String buildResultItemId = connection.createBuildResult(testName, personalWorkspace.getResolvedWorkspace().getName(), "my personal buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (failure[0] != null) {
				throw failure[0];
			}
			
			// wrap the build result info so that we can supply the build result uuid
			IBuildResultInfo buildResultInfoWrapper = new IBuildResultInfo() {
				
				@Override
				public void setScheduled(boolean isScheduled) {
					buildResultInfo.setScheduled(isScheduled);
				}
				
				@Override
				public void setRequestor(String requestor) {
					buildResultInfo.setRequestor(requestor);
				}
				
				@Override
				public void setPersonalBuild(boolean isPersonalBuild) {
					buildResultInfo.setPersonalBuild(isPersonalBuild);
				}
				
				@Override
				public String getBuildResultUUID() {
					// Just calling to make sure it is actually callable (we are doing reflection to get
					// the actual uuid)
					buildResultInfo.getBuildResultUUID();
					return buildResultItemId;
				}
			};
			BuildConnection buildConnection = new BuildConnection(repo);
			buildConnection.getBuildResultInfo(buildResultInfoWrapper, listener, new NullProgressMonitor());
			
			return repo.loggedInContributor().getName();
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}

	}
}
