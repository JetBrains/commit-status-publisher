/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.commitPublisher;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.RunningBuildState;
import jetbrains.buildServer.serverSide.impl.projects.ProjectsWatcher;
import jetbrains.buildServer.serverSide.impl.xml.XmlConstants;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import org.jdom.Element;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerTest extends CommitStatusPublisherTestBase {

  private final long TASK_COMPLETION_TIMEOUT_MS = 3000;

  private CommitStatusPublisherListener myListener;
  private MockPublisher myPublisher;
  private PublisherLogger myLogger;
  private SUser myUser;
  private Event myLastEventProcessed;
  private final Consumer<Event> myEventProcessedCallback = event -> myLastEventProcessed = event;
  private ConfigActionFactory myConfigActions;
  private ProjectPersistingHandler myProjectPersistingHandler;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLastEventProcessed = null;
    myLogger = new PublisherLogger();
    final PublisherManager myPublisherManager = new PublisherManager(myServer);
    final BuildHistory history = myFixture.getHistory();
    myListener = new CommitStatusPublisherListener(myFixture.getEventDispatcher(), myPublisherManager, history, myBuildsManager, myFixture.getBuildPromotionManager(), myProblems,
                                                   myFixture.getServerResponsibility(), myFixture.getSingletonService(ExecutorServices.class),
                                                   myFixture.getSingletonService(ProjectManager.class),
                                                   myMultiNodeTasks);
    myListener.setEventProcessedCallback(myEventProcessedCallback);
    myPublisher = new MockPublisher(myPublisherSettings, MockPublisherSettings.PUBLISHER_ID, myBuildType, myFeatureDescriptor.getId(),
                                    Collections.emptyMap(), myProblems, myLogger);
    myUser = myFixture.createUserAccount("newuser");
    myPublisherSettings.setPublisher(myPublisher);
    myProjectPersistingHandler = myFixture.getSingletonService(ProjectPersistingHandler.class);
    myConfigActions = myFixture.getSingletonService(ConfigActionFactory.class);
  }

  public void should_publish_started() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED));
  }


  public void should_publish_statuses_in_order() throws InterruptedException {
    prepareVcs();
    myPublisher.setEventToWait(Event.STARTED);
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningBuild, false);
    myPublisher.notifyWaitingEvent(Event.STARTED, 1000);
    waitFor(() -> myPublisher.getEventsReceived().equals(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED)), TASK_COMPLETION_TIMEOUT_MS);
  }

  @TestFor(issues = "TW-69618")
  public void should_mark_task_finished_before_publishing() throws InterruptedException {
    prepareVcs();
    myPublisher.setEventToWait(Event.STARTED);
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    myFixture.flushQueueAndWait();
    waitFor(() -> myMultiNodeTasks.findFinishedTasks(Collections.singleton(Event.STARTED.getName()), Dates.ONE_MINUTE).stream().findAny().isPresent(), TASK_COMPLETION_TIMEOUT_MS);
    myPublisher.notifyWaitingEvent(Event.STARTED, 1000);
    waitFor(() -> myPublisher.getEventsReceived().equals(Arrays.asList(Event.QUEUED, Event.STARTED)), TASK_COMPLETION_TIMEOUT_MS);
  }


  public void should_not_accept_pending_after_finished() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    List<Event> eventsAfterFinished = myPublisher.getEventsReceived();
    then(eventsAfterFinished).contains(Event.FINISHED);
    myListener.changesLoaded(runningBuild);
    waitForTasksToFinish(Event.STARTED);
    then(myPublisher.getEventsReceived()).isEqualTo(eventsAfterFinished);  // no more events must arrive at the publisher
  }

  public void should_accept_pending_after_build_triggered_with_comment() {
    prepareVcs();
    BuildCustomizerFactory customizerFactory = myFixture.getSingletonService(BuildCustomizerFactory.class);
    BuildCustomizer customizer = customizerFactory.createBuildCustomizer(myBuildType, myUser);
    customizer.setBuildComment("Non-empty comment");
    BuildPromotion promotion = customizer.createPromotion();
    SQueuedBuild queuedBuild = promotion.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    myFixture.waitForQueuedBuildToStart(queuedBuild);
    waitForTasksToFinish(Event.STARTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED,Event.STARTED));
  }

  public void should_publish_commented() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    runningBuild.setBuildComment(myUser , "My test comment");
    waitForTasksToFinish(Event.COMMENTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.COMMENTED));
    then(myPublisher.getLastComment()).isEqualTo("My test comment");
  }

  public void should_publish_failure() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myListener.buildChangedStatus(runningBuild, Status.NORMAL, Status.FAILURE);
    waitForTasksToFinish(Event.FAILURE_DETECTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FAILURE_DETECTED));
    then(myPublisher.isFailureReceived()).isTrue();
  }

  public void should_publish_queued() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED));
  }

  public void should_publish_interrupted() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    runningBuild.setInterrupted(RunningBuildState.INTERRUPTED_BY_USER, myUser, "My reason");
    finishBuild(false);
    waitForTasksToFinish(Event.INTERRUPTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.INTERRUPTED));
  }

  // this test fails after being rewritten to use the real event due to promotion manager
  // not being able to find promotion by id, so REMOVED_FROM_QUEUE event did never work properly
  @Test(enabled = false)
  public void should_publish_removed_from_queue() {
    prepareVcs();
    myBuildType.addToQueue("");
    myServer.getQueue().removeAllFromQueue();
    waitForTasksToFinish(Event.REMOVED_FROM_QUEUE);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.REMOVED_FROM_QUEUE));
  }

  public void should_publish_finished_success() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
    then(myPublisher.isSuccessReceived()).isTrue();
  }

  public void should_obey_publishing_disabled_property() {
    prepareVcs();
    setInternalProperty("teamcity.commitStatusPublisher.enabled", "false");
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_obey_publishing_disabled_parameter() {
    prepareVcs();
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.enabled", "false"));
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_give_a_priority_to_publishing_enabled_parameter() {
    prepareVcs();
    setInternalProperty("teamcity.commitStatusPublisher.enabled", "false");
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.enabled", "true"));
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
  }

  public void should_give_a_priority_to_publishing_disabled_parameter() {
    prepareVcs();
    setInternalProperty("teamcity.commitStatusPublisher.enabled", "true");
    myBuildType.getProject().addParameter(new SimpleParameter("teamcity.commitStatusPublisher.enabled", "false"));
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    myListener.buildFinished(runningBuild);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_publish_for_multiple_roots() {
    prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs2", "222", "rev2_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs3", "333", "rev3_2", SetVcsRootIdMode.DONT);
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    // This documents the current behaviour, i.e. only one QUEUED event received for some reason
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED,
                                                                  Event.STARTED, Event.STARTED, Event.STARTED,
                                                                  Event.FINISHED, Event.FINISHED, Event.FINISHED));
    then(myPublisher.successReceived()).isEqualTo(3);
  }

  public void should_publish_to_specified_root_with_multiple_roots_attached() {
    prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs2", "222", "rev2_2", SetVcsRootIdMode.EXT_ID);
    prepareVcs("vcs3", "333", "rev3_2", SetVcsRootIdMode.DONT);
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    // This documents the current behaviour, i.e. no QUEUED event received
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.STARTED, Event.FINISHED));
    then(myPublisher.successReceived()).isEqualTo(1);
  }

  public void should_handle_publisher_exception() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myPublisher.shouldThrowException();
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    SystemProblem problem = problems.iterator().next().getProblem();
    then(problem.getDescription())
      .contains("Commit Status Publisher")
      .contains("buildFinished")
      .contains(MockPublisher.PUBLISHER_ERROR)
      .contains(myPublisher.getId());
  }

  public void should_handle_async_errors() {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
  }

  public void should_retain_all_errors_for_multiple_roots() {
    prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs2", "222", "rev2_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs3", "333", "rev3_2", SetVcsRootIdMode.DONT);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger); // This problem should be cleaned during buildFinished(...)
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myPublisher.shouldReportError();
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    myProblems.reportProblem(myPublisher, "My build", null, null, myLogger); // and one more - later
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(4); // Must be 4 in total, neither 1 nor 5
  }


  public void should_not_publish_additional_status_if_marked_successful() {
    prepareVcs();
    myBuildType.addToQueue("");
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myListener.buildChangedStatus(runningBuild, Status.FAILURE, Status.NORMAL);
    assertIfNoTasks(Event.FAILURE_DETECTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED));
    then(myPublisher.isFailureReceived()).isFalse();
  }

  @Test
  @TestFor(issues = "TW-47724")
  public void should_not_publish_status_for_personal_builds() throws IOException {
    prepareVcs();
    SRunningBuild runningBuild = myFixture.startPersonalBuild(myUser, myBuildType);
    myFixture.finishBuild(runningBuild, false);
    assertIfNoTasks(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
    then(myPublisher.isSuccessReceived()).isFalse();
  }

  @Test
  @TestFor(issues = "TW-60688")
  public void should_clear_problem_on_feature_del() {
    prepareVcs();
    myProblems.reportProblem(myPublisher, "Build with feature", null, null, myLogger);
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myBuildType.removeBuildFeature(myBuildType.getBuildFeatures().iterator().next().getId());
    myListener.buildTypePersisted(myBuildType);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems).isEmpty();
  }

  @Test
  @TestFor(issues = "TW-60688")
  public void should_clear_problem_when_feature_disabled() {
    prepareVcs();
    myProblems.reportProblem(myPublisher, "Build with feature", null, null, myLogger);
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myBuildType.setEnabled(myFeatureDescriptor.getId(), false);
    myListener.buildTypePersisted(myBuildType);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems).isEmpty();
  }

  @Test(invocationCount = 200)
  @TestFor(issues = "TW-60688")
  public void should_clear_problem_when_xml_configuration_altered() {
    prepareVcs();
    myProblems.reportProblem(myPublisher, "Build with feature", null, null, myLogger);
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    File configurationFile = myBuildType.getConfigurationFile();
    FileUtil.processXmlFile(configurationFile, new FileUtil.Processor() {
      @Override
      public void process(Element rootElement) {
        boolean removed = rootElement.getChild(XmlConstants.SETTINGS).removeChild(XmlConstants.BUILD_EXTENSIONS);
        then(removed).isTrue();
      }
    });
    myFixture.getSingletonService(ProjectsWatcher.class).checkForModifications();
    SBuildType reloadedBuildType = myProjectManager.findBuildTypeByExternalId(myBuildType.getExternalId());
    then(reloadedBuildType).isNotNull();
    waitForAssert(() -> myProblemNotificationEngine.getProblems(reloadedBuildType).isEmpty(), 2000);
  }

  @TestFor(issues = "TW-60688")
  public void should_clear_problem_on_default_template_detached() {
    prepareVcs();
    SBuildFeatureDescriptor myBuildFeature = myBuildType.getBuildFeatures().iterator().next();
    myBuildType.removeBuildFeature(myBuildFeature.getId());
    BuildTypeTemplateEx template = myProject.createBuildTypeTemplate("template");
    template.addBuildFeature(myBuildFeature);
    myProject.setDefaultTemplate(template);
    myProblems.reportProblem(myPublisher, "Build with feature", null, null, myLogger);
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myListener.projectPersisted(myProject.getProjectId());
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myProject.setDefaultTemplate(null);
    myListener.projectPersisted(myProject.getProjectId());
    waitForAssert(() -> myProblemNotificationEngine.getProblems(myBuildType).isEmpty(), 2000);
  }

  @TestFor(issues = "TW-60688")
  public void should_clear_problem_on_move_to_another_project() {
    prepareVcs();
    SBuildFeatureDescriptor myBuildFeature = myBuildType.getBuildFeatures().iterator().next();
    myBuildType.removeBuildFeature(myBuildFeature.getId());
    BuildTypeTemplateEx template = myProject.createBuildTypeTemplate("template");
    template.addBuildFeature(myBuildFeature);
    myProject.setDefaultTemplate(template);
    myProblems.reportProblem(myPublisher, "Build with feature", null, null, myLogger);
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    SProject anotherProject = myProjectManager.createProject("anotherProject");
    myBuildType.getVcsRoots().forEach(vcsRoot -> myBuildType.removeVcsRoot(vcsRoot)); // to allow movement to another project
    myBuildType.moveToProject(anotherProject);
    then(myProblemNotificationEngine.getProblems(myBuildType)).isEmpty();
  }

  @TestFor(issues = "TW-60688")
  public void should_clear_problem_on_competent_execution() {
    prepareVcs();
    SBuildFeatureDescriptor myBuildFeature = myBuildType.getBuildFeatures().iterator().next();
    myBuildType.removeBuildFeature(myBuildFeature.getId());
    BuildTypeTemplateEx template = myProject.createBuildTypeTemplate("template");
    template.addBuildFeature(myBuildFeature);
    myProject.setDefaultTemplate(template);
    myProblems.reportProblem(myPublisher, "Build with feature", null, null, myLogger);
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myListener.projectPersisted(myProject.getProjectId());
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myProject.setDefaultTemplate(null);
    // mock situation when two parallel events happened
    myListener.projectPersisted(myProject.getProjectId());
    myListener.buildTypeTemplatePersisted(template);
    waitForAssert(() -> myProblemNotificationEngine.getProblems(myBuildType).isEmpty(), 2000);
  }

  private void prepareVcs() {
   prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.EXT_ID);
  }

  private void prepareVcs(String vcsRootName, String currentVersion, String revNo, SetVcsRootIdMode setVcsRootIdMode) {
    final SVcsRoot vcsRoot = myFixture.addVcsRoot("jetbrains.git", vcsRootName);
    switch (setVcsRootIdMode) {
      case EXT_ID:
        myPublisher.setVcsRootId(vcsRoot.getExternalId());
        break;
      case INT_ID:
        myPublisher.setVcsRootId(String.valueOf(vcsRoot.getId()));
    }
    myBuildType.addVcsRoot(vcsRoot);
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    myCurrentVersions.put(vcsRoot.getName(), currentVersion);
    myFixture.addModification(new ModificationData(new Date(),
            Collections.singletonList(new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file","1", "2")),
            "descr2", "user", vcsRootInstance, revNo, revNo));
  }

  private void waitForTasksToFinish(Event eventType) {
    waitForAssert(() -> {
      return myLastEventProcessed == eventType;
    }, TASK_COMPLETION_TIMEOUT_MS);
  }

  private void assertIfNoTasks(Event eventType) {
    then(myMultiNodeTasks.findTasks(Collections.singleton(eventType.getName())).isEmpty()).isTrue();
  }

  private enum SetVcsRootIdMode { DONT, EXT_ID, INT_ID }
}
