

package jetbrains.buildServer.commitPublisher;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import jetbrains.buildServer.QueuedBuild;
import jetbrains.buildServer.buildTriggers.vcs.ModificationDataBuilder;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.dependency.DependencyFactory;
import jetbrains.buildServer.serverSide.dependency.DependencyOptions;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.serverSide.impl.CancelableTaskHolder;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.impl.RunningBuildState;
import jetbrains.buildServer.serverSide.impl.beans.VcsRootInstanceContext;
import jetbrains.buildServer.serverSide.impl.projects.ProjectsWatcher;
import jetbrains.buildServer.serverSide.impl.xml.XmlConstants;
import jetbrains.buildServer.serverSide.systemProblems.BuildFeatureProblemsTicketManagerImpl;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserModel;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.http.HttpMethod;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootInstanceImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static jetbrains.buildServer.commitPublisher.CommitStatusPublisherListener.*;
import static jetbrains.buildServer.serverSide.impl.MultiNodeTasksDbImpl.PROCESS_TASKS_DELAY_MILLIS;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerTest extends CommitStatusPublisherTestBase {

  private final long TASK_COMPLETION_TIMEOUT_MS = 3000;

  private CommitStatusPublisherListener myListener;
  private MockPublisher myPublisher;
  private BuildFeatureProblemsTicketManagerImpl myTicketManager;
  private PublisherLogger myLogger;
  private SUser myUser;
  private Event myLastEventProcessed;
  private final Consumer<Event> myEventProcessedCallback = event -> myLastEventProcessed = event;
  private Map<String, SVcsRoot> myRoots;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLastEventProcessed = null;
    myLogger = new PublisherLogger();
    myListener = new CommitStatusPublisherListener(myFixture.getEventDispatcher(), new PublisherManager(myServer), myFixture.getHistory(), myBuildsManager, myFixture.getBuildPromotionManager(), myProblems,
                                                   myFixture.getServerResponsibility(), myFixture.getSingletonService(ExecutorServices.class),
                                                   myFixture.getSingletonService(ProjectManager.class), myFixture.getSingletonService(TeamCityNodes.class),
                                                   myFixture.getSingletonService(UserModel.class), myMultiNodeTasks);
    myListener.setEventProcessedCallback(myEventProcessedCallback);
    myPublisher = new MockPublisher(myPublisherSettings, MockPublisherSettings.PUBLISHER_ID, myBuildType, myFeatureDescriptor.getId(),
                                    Collections.emptyMap(), myProblems, myLogger, myWebLinks);
    myUser = myFixture.createUserAccount("newuser");
    myPublisherSettings.setPublisher(myPublisher);
    myPublisherSettings.setLinks(myWebLinks);
    myTicketManager = myFixture.findSingletonService(BuildFeatureProblemsTicketManagerImpl.class);
    myRoots = new HashMap<>();
  }
  
  private QueuedBuild addBuildToQueue(BuildPromotionEx buildPromotion) {
    QueuedBuildEx queuedBuild = (QueuedBuildEx)buildPromotion.getBuildType().addToQueue(buildPromotion, "");
    assertNotNull(queuedBuild);
    myListener.changesLoaded(queuedBuild.getBuildPromotion());
    return queuedBuild;
  }

  private QueuedBuild addBuildToQueue() {
    return addBuildToQueue("");
  }

  private QueuedBuild addBuildToQueue(@NotNull String triggeredBy) {
    QueuedBuildEx queuedBuild = (QueuedBuildEx)myBuildType.addToQueue(triggeredBy);
    assertNotNull(queuedBuild);
    queuedBuild.getBuildPromotion().getTopDependencyGraph().collectChangesForGraph(new CancelableTaskHolder());
    myListener.changesLoaded(queuedBuild.getBuildPromotion());
    return queuedBuild;
  }

  public void should_publish_started() {
    prepareVcs();
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED));
  }

  public void should_not_publish_remove_from_queue_before_start() {
    prepareVcs();
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    boolean receivedRemovedFromQueueComment = myPublisher.getCommentsReceived().stream()
                                            .anyMatch(comment -> comment.contains("removed from queue"));
    then(receivedRemovedFromQueueComment).isFalse();
  }

  public void should_publish_statuses_in_order() throws InterruptedException {
    prepareVcs();
    myPublisher.setEventToWait(Event.STARTED);
    addBuildToQueue();
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
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    myFixture.flushQueueAndWait();
    waitFor(() -> myMultiNodeTasks.findFinishedTasks(Collections.singleton(Event.STARTED.getName()), Dates.ONE_MINUTE).stream().findAny().isPresent(), TASK_COMPLETION_TIMEOUT_MS);
    myPublisher.notifyWaitingEvent(Event.STARTED, 1000);
    waitFor(() -> myPublisher.getEventsReceived().equals(Arrays.asList(Event.QUEUED, Event.STARTED)), TASK_COMPLETION_TIMEOUT_MS);
  }


  public void should_not_accept_pending_after_finished() {
    prepareVcs();
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    List<Event> eventsAfterFinished = myPublisher.getEventsReceived();
    then(eventsAfterFinished).contains(Event.FINISHED);
    myListener.changesLoaded(runningBuild.getBuildPromotion()); // won't execute at all, because build should be running to be processed
    then(myPublisher.getEventsReceived()).isEqualTo(eventsAfterFinished);  // no more events must arrive at the publisher
  }

  public void should_accept_pending_after_build_triggered_with_comment() {
    SVcsModification modification = prepareVcs();
    BuildCustomizerFactory customizerFactory = myFixture.getSingletonService(BuildCustomizerFactory.class);
    BuildCustomizer customizer = customizerFactory.createBuildCustomizer(myBuildType, myUser);
    customizer.setBuildComment("Non-empty comment");
    customizer.setChangesUpTo(modification);
    BuildPromotionEx promotion = (BuildPromotionEx)customizer.createPromotion();
    SQueuedBuild queuedBuild = (SQueuedBuild)addBuildToQueue(promotion);
    waitForTasksToFinish(Event.QUEUED);
    myFixture.waitForQueuedBuildToStart(queuedBuild);
    waitForTasksToFinish(Event.STARTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.COMMENTED, Event.STARTED));
  }

  public void should_publish_commented() {
    prepareVcs();
    addBuildToQueue();
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
    addBuildToQueue();
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
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    waitForAssert(() -> !myPublisher.getEventsReceived().isEmpty(), TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED));
  }

  public void should_publish_interrupted() {
    prepareVcs();
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    runningBuild.setInterrupted(RunningBuildState.INTERRUPTED_BY_USER, myUser, "My reason");
    finishBuild(false);
    waitForTasksToFinish(Event.INTERRUPTED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.INTERRUPTED));
  }

  public void should_publish_removed_from_queue() {
    prepareVcs();
    QueuedBuild queuedBuild = addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    waitForAssert(() -> myPublisher.getLastComment().equals(DefaultStatusMessages.BUILD_QUEUED), TASK_COMPLETION_TIMEOUT_MS);
    myServer.getQueue().removeAllFromQueue(queuedBuild.getBuildTypeId(), myUser, null);
    waitFor(() -> myPublisher.getLastComment() != null && myPublisher.getLastComment().equals(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE), TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getLastComment()).isEqualTo(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE);
  }

  public void should_publish_finished_success() {
    prepareVcs();
    addBuildToQueue();
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
    setInternalProperty(CommitStatusPublisherListener.PUBLISHING_ENABLED_PROPERTY_NAME, "false");
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_obey_publishing_disabled_parameter() {
    prepareVcs();
    myBuildType.getProject().addParameter(new SimpleParameter(CommitStatusPublisherListener.PUBLISHING_ENABLED_PROPERTY_NAME, "false"));
    SRunningBuild runningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  public void should_give_a_priority_to_publishing_enabled_parameter() {
    prepareVcs();
    setInternalProperty(CommitStatusPublisherListener.PUBLISHING_ENABLED_PROPERTY_NAME, "false");
    myBuildType.getProject().addParameter(new SimpleParameter(CommitStatusPublisherListener.PUBLISHING_ENABLED_PROPERTY_NAME, "true"));
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
  }

  public void should_give_a_priority_to_publishing_disabled_parameter() {
    prepareVcs();
    setInternalProperty(CommitStatusPublisherListener.PUBLISHING_ENABLED_PROPERTY_NAME, "true");
    myBuildType.getProject().addParameter(new SimpleParameter(CommitStatusPublisherListener.PUBLISHING_ENABLED_PROPERTY_NAME, "false"));
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
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    waitForAssert(() -> myPublisher.getEventsReceived().size() >= 3, TASK_COMPLETION_TIMEOUT_MS);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.QUEUED, Event.QUEUED,
                                                                  Event.STARTED, Event.STARTED, Event.STARTED,
                                                                  Event.FINISHED, Event.FINISHED, Event.FINISHED));
    then(myPublisher.successReceived()).isEqualTo(3);
  }

  public void should_publish_to_specified_root_with_multiple_roots_attached() {
    prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.DONT);
    prepareVcs("vcs2", "222", "rev2_2", SetVcsRootIdMode.EXT_ID);
    prepareVcs("vcs3", "333", "rev3_2", SetVcsRootIdMode.DONT);
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
    then(myPublisher.successReceived()).isEqualTo(1);
  }

  public void should_handle_publisher_exception() {
    prepareVcs();
    addBuildToQueue();
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
      .contains("Failed to publish status for the build")
      .contains("buildFinished")
      .contains(MockPublisher.PUBLISHER_ERROR)
      .contains(myPublisher.toString());
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
    addBuildToQueue();
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
    waitForTasksToFinish(Event.FINISHED);
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
    myTicketManager.buildTypePersisted(myBuildType);
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
    myTicketManager.buildTypePersisted(myBuildType);
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems).isEmpty();
  }

  @Test
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
    waitForAssert(() -> myProblemNotificationEngine.getProblems(reloadedBuildType).isEmpty(), TASK_COMPLETION_TIMEOUT_MS);
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
    myTicketManager.projectPersisted(myProject.getProjectId());
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myProject.setDefaultTemplate(null);
    myTicketManager.projectPersisted(myProject.getProjectId());
    waitForAssert(() -> myProblemNotificationEngine.getProblems(myBuildType).isEmpty(), TASK_COMPLETION_TIMEOUT_MS);
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
    myTicketManager.projectPersisted(myProject.getProjectId());
    then(myProblemNotificationEngine.getProblems(myBuildType).size()).isEqualTo(1);
    myProject.setDefaultTemplate(null);
    // mock situation when two parallel events happened
    myTicketManager.projectPersisted(myProject.getProjectId());
    myTicketManager.buildTypeTemplatePersisted(template);
    waitForAssert(() -> myProblemNotificationEngine.getProblems(myBuildType).isEmpty(), TASK_COMPLETION_TIMEOUT_MS);
  }

  public void should_not_pass_through_comment_and_user() {
    prepareVcs();
    SQueuedBuild myBuild = (SQueuedBuild)addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    final String removeFromQueueComment = "Test comment for remove from queue";
    myBuild.removeFromQueue(myUser, removeFromQueueComment);
    final String expectedComment = DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE;
    waitForAssert(() -> expectedComment.equals(myPublisher.getLastComment()), TASK_COMPLETION_TIMEOUT_MS);
    then(myPublisher.getLastUser()).isEqualTo(myUser);
  }

  public void should_not_pass_through_user_comment_on_build_delete_from_queue() {
    prepareVcs();
    SQueuedBuild queuedBuild = (SQueuedBuild)addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    final String comment = "Comment, received from AJAX query";
    myFixture.getBuildQueue().removeQueuedBuilds(Collections.singleton(queuedBuild), myUser, comment);
    final String expectedComment = DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE;
    waitForAssert(() -> expectedComment.equals(myPublisher.getLastComment()), TASK_COMPLETION_TIMEOUT_MS);
    waitForAssert(() -> myPublisher.getLastUser().equals(myUser), TASK_COMPLETION_TIMEOUT_MS);
  }

  public void should_not_publish_queued_status_for_new_commit_for_queued_build() {
    prepareVcs();
    QueuedBuildEx queuedBuild = (QueuedBuildEx)addBuildToQueue();
    assertNotNull(queuedBuild);
    waitForTasksToFinish(Event.QUEUED);
    waitForAssert(() -> myPublisher.getCommentsReceived().size() == 1, TASK_COMPLETION_TIMEOUT_MS);

    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    VcsRootInstanceImpl root = new VcsRootInstanceImpl(vcsRootInstance.getId(), vcsRootInstance.getVcsName(), vcsRootInstance.getParentId(), vcsRootInstance.getName(),
                                                       vcsRootInstance.getProperties(), myFixture.getSingletonService(VcsRootInstanceContext.class));
    myFixture.addModification(new ModificationData(new Date(), Collections.singletonList(new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file",
                                                                                                       "file", "2", "3")),
                                                                                       "descr", "user", root, "rev1_3", "rev1_3"));
    myListener.changesLoaded(queuedBuild.getBuildPromotion());
    waitFor(() -> myPublisher.getCommentsReceived().size() == 1, TASK_COMPLETION_TIMEOUT_MS);
  }

  @Test(enabled = false)  // Should pass after TW-75702
  public void should_not_publish_queued_status_because_of_checkout_rule() {
    prepareVcs();
    final SVcsRoot vcsRoot = myBuildType.getVcsRoots().iterator().next();
    CheckoutRules checkoutRules = new CheckoutRules("+:src => .");
    myBuildType.setCheckoutRules(vcsRoot, checkoutRules);

    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);

    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    VcsRootInstanceImpl root = new VcsRootInstanceImpl(vcsRootInstance.getId(), vcsRootInstance.getVcsName(), vcsRootInstance.getParentId(), vcsRootInstance.getName(),
                                                                   vcsRootInstance.getProperties(), myFixture.getSingletonService(VcsRootInstanceContext.class));

    SVcsModification modification = myFixture.addModification(new ModificationData(new Date(),
                                                                                   Collections.singletonList(
                                                                                     new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file", "2", "3")),
                                                                                   "descr", "user", root, "rev1_3", "rev1_3"));
    myListener.changeAdded(modification, root, Collections.singleton(myBuildType));

    RunningBuildEx runningBuild = myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);
    myFixture.finishBuild(runningBuild, false);
    waitForTasksToFinish(Event.FINISHED);

    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED, Event.FINISHED));
  }

  public void should_publish_queued_on_passed_revision() throws PublisherException {
    prepareVcs();
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    SVcsModification modififcation1 = myFixture.addModification(new ModificationData(new Date(),
                                                                                       Collections.singletonList(
                                                                                         new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file", "2", "3")),
                                                                                       "Should publish on this", "user", vcsRootInstance, "rev1_3", "rev1_3"));
    SVcsModification modififcation2 = myFixture.addModification(new ModificationData(new Date(),
                                                                                     Collections.singletonList(
                                                                                       new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file", "3", "4")),
                                                                                     "Should ignore this", "user", vcsRootInstance, "rev1_4", "rev1_4"));
    BuildRevisionEx revision1 = new BuildRevisionEx(vcsRootInstance, modififcation1.getVersion(), "", modififcation1.getDisplayVersion());
    BuildPromotionEx buildPromotion = myBuildType.createBuildPromotion("branch1");
    buildPromotion.setBuildRevisions(Arrays.asList(revision1), modififcation1.getId(), Long.MAX_VALUE);
    addBuildToQueue(buildPromotion);
    waitForTasksToFinish(Event.QUEUED);
    then(myPublisher.getLastTargetRevision()).isEqualTo(modififcation1.getVersion());
    then(myPublisher.getPublishingTargetRevisions()).doesNotContain(modififcation2.getVersion());
  }

  public void should_be_sent_excepted_number_of_requests_for_build_chain() {
    prepareVcs();
    String projectName = myProject.getName();
    SBuildFeatureDescriptor myBuildFeature = myBuildType.getBuildFeatures().iterator().next();
    SVcsRoot commonVcsRoot = myBuildType.getVcsRoots().iterator().next();
    VcsRootInstance commonVcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    int version = 42;

    BuildTypeImpl bt2 = registerBuildType("bt2", projectName);
    bt2.addBuildFeature(myBuildFeature);
    bt2.addVcsRoot(commonVcsRoot);
    myFixture.addModification(ModificationDataBuilder.modification().in(commonVcsRootInstance).version(version++), bt2, RelationType.REGULAR);
    BuildTypeImpl bt3 = registerBuildType("bt3", projectName);
    bt3.addBuildFeature(myBuildFeature);
    bt3.addVcsRoot(commonVcsRoot);
    myFixture.addModification(ModificationDataBuilder.modification().in(commonVcsRootInstance).version(version++), bt3, RelationType.REGULAR);
    BuildTypeImpl bt4 = registerBuildType("bt4", projectName);
    bt4.addBuildFeature(myBuildFeature);
    bt4.addVcsRoot(commonVcsRoot);
    myFixture.addModification(ModificationDataBuilder.modification().in(commonVcsRootInstance).version(version), bt4, RelationType.REGULAR);
    DependencyFactory dependencyFactory = myFixture.getSingletonService(DependencyFactory.class);
    myBuildType.addDependency(dependencyFactory.createDependency(bt2));
    myBuildType.addDependency(dependencyFactory.createDependency(bt3));
    bt2.addDependency(dependencyFactory.createDependency(bt4));
    bt3.addDependency(dependencyFactory.createDependency(bt4));

    assertEquals(0, myPublisher.getSentRequests().size());
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 4, TASK_COMPLETION_TIMEOUT_MS);

    waitForNRequestsToBeSent(5); // 4 x GET + 1 x POST

    assertEquals(4L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.GET).count()); // 4 x GET current status (for each build type)
    assertEquals(1L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.POST).count()); // 1 x POST new status (we publish queued only when there is no other statuses for revision)
  }

  public void should_be_sent_excepted_number_of_requests_for_build_chain_toggle_off() {
    prepareVcs();
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "false");
    String projectName = myProject.getName();
    SBuildFeatureDescriptor myBuildFeature = myBuildType.getBuildFeatures().iterator().next();
    SVcsRoot commonVcsRoot = myBuildType.getVcsRoots().iterator().next();
    VcsRootInstance commonVcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    int version = 42;

    BuildTypeImpl bt2 = registerBuildType("bt2", projectName);
    bt2.addBuildFeature(myBuildFeature);
    bt2.addVcsRoot(commonVcsRoot);
    myFixture.addModification(ModificationDataBuilder.modification().in(commonVcsRootInstance).version(version++), bt2, RelationType.REGULAR);
    BuildTypeImpl bt3 = registerBuildType("bt3", projectName);
    bt3.addBuildFeature(myBuildFeature);
    bt3.addVcsRoot(commonVcsRoot);
    myFixture.addModification(ModificationDataBuilder.modification().in(commonVcsRootInstance).version(version++), bt3, RelationType.REGULAR);
    BuildTypeImpl bt4 = registerBuildType("bt4", projectName);
    bt4.addBuildFeature(myBuildFeature);
    bt4.addVcsRoot(commonVcsRoot);
    myFixture.addModification(ModificationDataBuilder.modification().in(commonVcsRootInstance).version(version), bt4, RelationType.REGULAR);
    DependencyFactory dependencyFactory = myFixture.getSingletonService(DependencyFactory.class);
    myBuildType.addDependency(dependencyFactory.createDependency(bt2));
    myBuildType.addDependency(dependencyFactory.createDependency(bt3));
    bt2.addDependency(dependencyFactory.createDependency(bt4));
    bt3.addDependency(dependencyFactory.createDependency(bt4));


    assertEquals(0, myPublisher.getSentRequests().size());
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 4, TASK_COMPLETION_TIMEOUT_MS);

    waitForNRequestsToBeSent(4); // we post 4 queued statuses
    assertEquals(4L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.POST).count());
    assertEquals(0L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.GET).count());
  }

  private void waitForNRequestsToBeSent(int expected) {
    try {
      waitFor(() -> myPublisher.getSentRequests().size() >= expected, TASK_COMPLETION_TIMEOUT_MS);
    } catch (AssertionError e) {
      fail(String.format("Only %d requests was sent, when it was expexted %d", myPublisher.getSentRequests().size(), expected));
    }
  }

  public void should_be_sent_excepted_number_of_requests_for_single_build() {
    prepareVcs();
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    waitFor(() -> myPublisher.getSentRequests().size() == 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(1L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.POST).count());
    assertEquals(1L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.GET).count());
  }

  public void should_be_sent_excepted_number_of_requests_for_single_build_toggle_on() {
    prepareVcs();
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "true");
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    waitFor(() -> myPublisher.getSentRequests().size() == 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(1L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.POST).count());
    assertEquals(1L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.GET).count());
  }

  public long getCntPostRequests() {
    return myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.POST).count();
  }

  @TestFor(issues = "TW-34249")
  public void should_retry_on_failure() {
    prepareVcs();
    setInternalProperty(RETRY_ENABLED_PROPERTY_NAME, true);
    setInternalProperty(RETRY_INITAL_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_DELAY_PROPERTY_NAME, 10);
    setInternalProperty(PROCESS_TASKS_DELAY_MILLIS, 1);
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "false");
    myPublisher.shouldFailToPublish(3);
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    waitFor(() -> getCntPostRequests() >= 4, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(4L, getCntPostRequests());
    waitForTasksToFinish(Event.QUEUED);
  }

  @TestFor(issues = "TW-34249")
  public void should_retry_on_get_revision_status_failure() {
    prepareVcs();
    setInternalProperty(RETRY_ENABLED_PROPERTY_NAME, true);
    setInternalProperty(RETRY_INITAL_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_DELAY_PROPERTY_NAME, 2);
    setInternalProperty(PROCESS_TASKS_DELAY_MILLIS, 1);
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "true");

    myPublisher.shouldFailToPublish(1000);
    addBuildToQueue();
    waitFor(() -> myPublisher.getSentRequests().size() >= 3, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(3L, myPublisher.getSentRequests().size());
  }

  @TestFor(issues = "TW-34249")
  public void should_retry_on_failure_no_more_than_1_time() {
    prepareVcs();
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "false");
    setInternalProperty(RETRY_ENABLED_PROPERTY_NAME, true);
    setInternalProperty(RETRY_INITAL_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(PROCESS_TASKS_DELAY_MILLIS, 1);
    myPublisher.shouldFailToPublish(1000);
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    waitFor(() -> getCntPostRequests() >= 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(2L, getCntPostRequests());

    myFixture.flushQueueAndWait();
    waitFor(() -> getCntPostRequests() >= 4, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(4L, getCntPostRequests());
    waitForTasksToFinish(Event.STARTED);
  }

  @TestFor(issues = "TW-34249")
  public void should_retry_on_all_possible_event_types() {
    prepareVcs();
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "false");
    setInternalProperty(RETRY_ENABLED_PROPERTY_NAME, true);
    setInternalProperty(RETRY_INITAL_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_DELAY_PROPERTY_NAME, 10);
    setInternalProperty(PROCESS_TASKS_DELAY_MILLIS, 1);

    myPublisher.shouldFailToPublish(1);
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    waitFor(() -> getCntPostRequests() >= 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(2L, getCntPostRequests());
    waitForTasksToFinish(Event.QUEUED);

    myPublisher.shouldFailToPublish(1);
    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitFor(() -> getCntPostRequests() >= 4, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(4L, getCntPostRequests());
    waitForTasksToFinish(Event.STARTED);

    myPublisher.shouldFailToPublish(1);
    runningBuild.setBuildComment(myUser , "My test comment");
    waitFor(() -> getCntPostRequests() >= 6, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(6L, getCntPostRequests());
    waitForTasksToFinish(Event.COMMENTED);

    myPublisher.shouldFailToPublish(1);
    myListener.buildChangedStatus(runningBuild, Status.NORMAL, Status.FAILURE);
    waitFor(() -> getCntPostRequests() >= 8, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(8L, getCntPostRequests());
    waitForTasksToFinish(Event.FAILURE_DETECTED);

    myPublisher.shouldFailToPublish(1);
    myFixture.finishBuild(runningBuild, false);
    waitFor(() -> getCntPostRequests() >= 10, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(10L, getCntPostRequests());
    waitForTasksToFinish(Event.FINISHED);
  }

  @TestFor(issues = "TW-34249")
  public void should_stop_retrying_if_service_is_not_available_for_long_time() {
    prepareVcs();
    setInternalProperty(RETRY_ENABLED_PROPERTY_NAME, true);
    setInternalProperty(RETRY_INITAL_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_TIME_BEFORE_DISABLING, 5);
    setInternalProperty(PROCESS_TASKS_DELAY_MILLIS, 1);
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "false");


    myPublisher.shouldFailToPublish(100);
    addBuildToQueue();
    waitFor(() -> getCntPostRequests() >= 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(2L, getCntPostRequests());
    waitForTasksToFinish(Event.QUEUED);

    try {
      Thread.sleep(5);
    } catch (InterruptedException ignored) {
    }

    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitFor(() -> getCntPostRequests() >= 3, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(3L, getCntPostRequests());
    waitForTasksToFinish(Event.STARTED);

    runningBuild.setBuildComment(myUser , "My test comment");
    waitFor(() -> getCntPostRequests() >= 4, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(4L, getCntPostRequests());
    waitForTasksToFinish(Event.COMMENTED);
  }

  @TestFor(issues = "TW-34249")
  public void should_enable_retrying_if_successfully_published_status () {
    prepareVcs();
    setInternalProperty(RETRY_ENABLED_PROPERTY_NAME, true);
    setInternalProperty(RETRY_INITAL_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_DELAY_PROPERTY_NAME, 1);
    setInternalProperty(RETRY_MAX_TIME_BEFORE_DISABLING, 5);
    setInternalProperty(PROCESS_TASKS_DELAY_MILLIS, 1);
    setInternalProperty(CHECK_STATUS_BEFORE_PUBLISHING, "false");

    myPublisher.shouldFailToPublish(3);
    addBuildToQueue();
    waitFor(() -> getCntPostRequests() >= 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(2L, myPublisher.getSentRequests().stream().filter(method -> method == HttpMethod.POST).count());
    waitForTasksToFinish(Event.QUEUED);

    try {
      Thread.sleep(5);
    } catch (InterruptedException ignored) {
    }

    SRunningBuild runningBuild = myFixture.flushQueueAndWait();
    waitFor(() -> getCntPostRequests() >= 3, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(3L, getCntPostRequests());
    waitForTasksToFinish(Event.STARTED);

    runningBuild.setBuildComment(myUser , "My test comment");
    waitFor(() -> getCntPostRequests() >= 4, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(4L, getCntPostRequests());
    waitForTasksToFinish(Event.COMMENTED);

    myPublisher.shouldFailToPublish(100);
    myFixture.finishBuild(runningBuild, false);
    waitFor(() -> getCntPostRequests() >= 6, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(6L, getCntPostRequests());
    waitForTasksToFinish(Event.FINISHED);
  }

  @TestFor(issues = "TW-83345")
  public void should_publish_failed_status_for_build_canceled_due_to_failed_dependency() {
    prepareVcs();

    BuildTypeImpl failBt = myFixture.createBuildType(myBuildType.getProject(), "failing_build", "Ant");
    DependencyFactory df = myFixture.getSingletonService(DependencyFactory.class);
    DependencyOptions opts = new DependencyOptionSupportImpl();
    opts.setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.CANCEL);
    myBuildType.addDependency(df.createDependency(failBt.getExternalId(), opts));

    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 2, TASK_COMPLETION_TIMEOUT_MS);
    waitFor(() -> getCntPostRequests() == 1, TASK_COMPLETION_TIMEOUT_MS);
    RunningBuildEx runningFailedBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningFailedBuild, true);
    myFixture.flushQueue();
    waitFor(() -> getCntPostRequests() == 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(2, myPublisher.getEventsReceived().size());
    assertEquals(Event.REMOVED_FROM_QUEUE, myPublisher.getEventsReceived().get(1));
    then(myPublisher.getLastComment()).isEqualTo(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED);
  }

  @TestFor(issues = "TW-84721")
  public void should_publish_failed_status_for_build_marked_as_failed_to_start_due_to_failed_dependency() {
    prepareVcs();

    BuildTypeImpl failBt = myFixture.createBuildType(myBuildType.getProject(), "failing_build", "Ant");
    DependencyFactory df = myFixture.getSingletonService(DependencyFactory.class);
    DependencyOptions opts = new DependencyOptionSupportImpl();
    opts.setOption(DependencyOptions.RUN_BUILD_IF_DEPENDENCY_FAILED, DependencyOptions.BuildContinuationMode.MAKE_FAILED_TO_START);
    myBuildType.addDependency(df.createDependency(failBt.getExternalId(), opts));

    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 2, TASK_COMPLETION_TIMEOUT_MS);
    waitFor(() -> getCntPostRequests() == 1, TASK_COMPLETION_TIMEOUT_MS);
    RunningBuildEx runningFailedBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningFailedBuild, true);
    myFixture.flushQueue();
    waitFor(() -> getCntPostRequests() == 2, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(2, myPublisher.getEventsReceived().size());
    assertEquals(Event.REMOVED_FROM_QUEUE, myPublisher.getEventsReceived().get(1));
    then(myPublisher.getLastComment()).isEqualTo(DefaultStatusMessages.BUILD_REMOVED_FROM_QUEUE_AS_CANCELED);
  }

  public void should_not_publish_queued_for_build_to_optimized() {
    prepareVcs();
    String projectName = myProject.getName();
    SBuildFeatureDescriptor myBuildFeature = myBuildType.getBuildFeatures().iterator().next();
    SVcsRoot commonVcsRoot = myBuildType.getVcsRoots().iterator().next();
    BuildTypeImpl bt2 = registerBuildType("bt2", projectName);
    bt2.addBuildFeature(myBuildFeature);
    bt2.addVcsRoot(commonVcsRoot);
    myFixture.addDependency(myBuildType, bt2, true);
    createBuild(bt2, Status.NORMAL);

    assertEquals(2, myPublisher.getSentRequests().size());
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 2, TASK_COMPLETION_TIMEOUT_MS);
    RunningBuildEx runningBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningBuild, false);
    myFixture.flushQueue();
    waitForNRequestsToBeSent(5);

    then(myPublisher.getEventsReceived()).doesNotContain(Event.QUEUED);
  }

  @Test
  public void should_publish_featureless_for_non_dependency_builds() {
    // Given
    prepareVcs();
    final SBuildFeatureDescriptor buildFeature = myBuildType.getBuildFeatures().iterator().next();
    myBuildType.removeBuildFeature(buildFeature.getId());
    myPublisherSettings.enableFeatureLessPublishing();

    // When
    addBuildToQueue();
    waitForTasksToFinish(Event.QUEUED);
    myFixture.flushQueueAndWait();
    waitForTasksToFinish(Event.STARTED);

    then(myPublisher.getEventsReceived()).isEqualTo(Arrays.asList(Event.QUEUED, Event.STARTED));
  }

  @Test
  public void should_not_publish_featureless_for_dependency_builds() {
    // Given
    prepareVcs();
    final SBuildFeatureDescriptor buildFeature = myBuildType.getBuildFeatures().iterator().next();
    myBuildType.removeBuildFeature(buildFeature.getId());
    myPublisherSettings.enableFeatureLessPublishing();

    // When
    final TriggeredByBuilder triggeredByBuilder = new TriggeredByBuilder();
    triggeredByBuilder.addParameter(TriggeredByBuilder.TYPE_PARAM_NAME, "snapshotDependency");
    addBuildToQueue(triggeredByBuilder.toString());
    myFixture.flushQueueAndWait();

    then(myPublisher.getEventsReceived()).isEmpty();
  }

  @TestFor(issues = "TW-84882")
  public void should_not_publish_when_collecting_changes_fails() {
    addBadVcsRootTo(myBuildType);
    final SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    assertNotNull(queuedBuild);
    myServer.flushQueue();

    waitFor(() -> {
      SBuild build = queuedBuild.getBuildPromotion().getAssociatedBuild();
      return build != null && build.isFinished();
    });

    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).isEqualTo(Collections.emptyList());
  }

  @TestFor(issues = "TW-84882")
  public void should_publish_to_fallback_when_collecting_changes_fails() {
    addBadVcsRootTo(myBuildType);
    final VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    final BuildRevision fallback = new BuildRevision(vcsRootInstance, "000", "", "000");
    myPublisher.setFallbackRevisions(Collections.singletonList(fallback));
    final SQueuedBuild queuedBuild = myBuildType.addToQueue("");
    assertNotNull(queuedBuild);
    myServer.flushQueue();

    waitFor(() -> {
      SBuild build = queuedBuild.getBuildPromotion().getAssociatedBuild();
      return build != null && build.isFinished();
    });

    waitForTasksToFinish(Event.FINISHED);
    then(myPublisher.getEventsReceived()).containsExactly(Event.STARTED, Event.FAILURE_DETECTED, Event.FINISHED);
  }

  @Test
  public void should_not_publish_commented_status_for_old_builds() {
    prepareVcs();
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    RunningBuildEx runningBuild = myFixture.flushQueueAndWait();
    SFinishedBuild oldFinishedBuild = myFixture.finishBuild(runningBuild, false);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(3, myPublisher.getEventsReceived().size());

    addBuildToQueue();
    runningBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningBuild, false);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED && myPublisher.getEventsReceived().size() > 3, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(5, myPublisher.getEventsReceived().size());

    oldFinishedBuild.setBuildComment(myUser, "test");

    // just to verify that previous status wasn't posted
    prepareVcs("vcs1", "113", "rev1_4", SetVcsRootIdMode.EXT_ID);
    addBuildToQueue();
    waitFor(() -> myPublisher.getLastEvent() != Event.FINISHED);
    runningBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningBuild, false);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(8, myPublisher.getEventsReceived().size());
  }

  @Test
  public void should_not_publish_status_on_marked_as_successful_event_for_old_builds() {
    prepareVcs();
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    RunningBuildEx runningBuild = myFixture.flushQueueAndWait();
    SFinishedBuild oldFinishedBuild = myFixture.finishBuild(runningBuild, true);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(4, myPublisher.getEventsReceived().size());

    addBuildToQueue();
    runningBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningBuild, true);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED && myPublisher.getEventsReceived().size() > 4, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(7, myPublisher.getEventsReceived().size());

    oldFinishedBuild.muteBuildProblems(myUser, true,"test");

    // just to verify that previous status wasn't posted
    prepareVcs("vcs1", "112", "rev1_3", SetVcsRootIdMode.EXT_ID);
    addBuildToQueue();
    waitFor(() -> myPublisher.getLastEvent() != Event.FINISHED);
    runningBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningBuild, false);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(10, myPublisher.getEventsReceived().size());
  }

  @Test
  public void should_publish_status_on_marked_as_successful_event_on_latest_build() {
    prepareVcs();
    addBuildToQueue();
    waitFor(() -> myFixture.getBuildQueue().getNumberOfItems() == 1, TASK_COMPLETION_TIMEOUT_MS);
    RunningBuildEx runningBuild = myFixture.flushQueueAndWait();
    myFixture.finishBuild(runningBuild, true);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(4, myPublisher.getEventsReceived().size());

    addBuildToQueue();
    runningBuild = myFixture.flushQueueAndWait();
    SBuild lastBuild = myFixture.finishBuild(runningBuild, true);
    waitFor(() -> myPublisher.getLastEvent() == Event.FINISHED && myPublisher.getEventsReceived().size() > 4, TASK_COMPLETION_TIMEOUT_MS);
    assertEquals(7, myPublisher.getEventsReceived().size());

    lastBuild.muteBuildProblems(myUser, true, "test");
    waitFor(() -> myPublisher.getLastEvent() == Event.MARKED_AS_SUCCESSFUL, TASK_COMPLETION_TIMEOUT_MS);
  }


  @DataProvider
  public static Object[][] buildUrls() {
    return new Object[][]{
      {"http://localhost:8111/bs/buildConfiguration/GitHubApp_2_Build1/24401", 24401L},
      {"http://localhost:8111/bs/buildConfiguration/GitHubApp_2_Build1/", null},
      {"http://localhost:8111/bs/viewLog.html?buildId=12121&buildTypeId=Build1", 12121L},
      {"http://localhost:8111/bs/viewLog.html?buildId=12121&buildTypeId=1323223", 12121L},
      {"http://localhost:8111/bs/viewLog.html?buildTypeId=1323223", null}
    };
  }

  @Test(dataProvider = "buildUrls")
  public void should_parse_build_id_from_old_and_new_build_urls(@NotNull String buildUrl, Long buildId) {
    BaseCommitStatusPublisher publisher = new BaseCommitStatusPublisher(myPublisherSettings, myBuildType, "", Collections.emptyMap(), myProblems) {
      @Override
      protected WebLinks getLinks() {
        return null;
      }

      @NotNull
      @Override
      public String getId() {
        return "test";
      }
    };

    assertEquals(buildId, publisher.getBuildIdFromViewUrl(buildUrl));
  }

  private SVcsModification prepareVcs() {
    return prepareVcs("vcs1", "111", "rev1_2", SetVcsRootIdMode.EXT_ID);
  }

  private SVcsModification prepareVcs(String vcsRootName, String currentVersion, String revNo, SetVcsRootIdMode setVcsRootIdMode) {
    final SVcsRoot vcsRoot;
    if (!myRoots.containsKey(vcsRootName)) {
      vcsRoot = myFixture.addVcsRoot("jetbrains.git", vcsRootName);
      myBuildType.addVcsRoot(vcsRoot);
      myRoots.put(vcsRootName, vcsRoot);
    } else {
      vcsRoot = myRoots.get(vcsRootName);
    }
    switch (setVcsRootIdMode) {
      case EXT_ID:
        myPublisher.setVcsRootId(vcsRoot.getExternalId());
        break;
      case INT_ID:
        myPublisher.setVcsRootId(String.valueOf(vcsRoot.getId()));
    }
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstances().stream()
                                                 .filter(vcsInstance -> vcsInstance.getParent().getId() == vcsRoot.getId())
                                                 .findFirst().get();
    myCurrentVersions.put(vcsRoot.getName(), currentVersion);
    return myFixture.addModification(new ModificationData(new Date(),
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

  private void addBadVcsRootTo(final BuildTypeEx addTo) {
    final MockVcsSupport failSupport = new MockVcsSupport("fail"){
      @NotNull
      @Override
      public String getCurrentVersion(@NotNull final VcsRoot root) {
        throw new RuntimeException("BAD VCS ROOT");
      }
    };

    myServer.getVcsManager().registerVcsSupport(failSupport);
    myFixture.addVcsRoot("fail", "", addTo);
  }

  private enum SetVcsRootIdMode { DONT, EXT_ID, INT_ID }
}