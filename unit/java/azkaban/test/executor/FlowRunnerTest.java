package azkaban.test.executor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import junit.framework.Assert;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlow.ExecutableNode;
import azkaban.executor.ExecutableFlow.FailureAction;
import azkaban.executor.FlowRunner;
import azkaban.executor.ExecutableFlow.Status;
import azkaban.executor.event.Event.Type;
import azkaban.flow.Flow;
import azkaban.utils.JSONUtils;

public class FlowRunnerTest {
	private File workingDir;
	private Logger logger = Logger.getLogger(FlowRunnerTest.class);
	
	public FlowRunnerTest() {
		
	}
	
	@Before
	public void setUp() throws Exception {
		System.out.println("Create temp dir");
		workingDir = new File("_AzkabanTestDir_" + System.currentTimeMillis());
		if (workingDir.exists()) {
			FileUtils.deleteDirectory(workingDir);
		}
		workingDir.mkdirs();
	}
	
	@After
	public void tearDown() throws IOException {
		System.out.println("Teardown temp dir");
		if (workingDir != null) {
			FileUtils.deleteDirectory(workingDir);
			workingDir = null;
		}
	}
	
	@Test
	public void exec1Normal() throws Exception {
		File testDir = new File("unit/executions/exectest1");
		ExecutableFlow exFlow = prepareExecDir(testDir, "exec1");
		
		EventCollectorListener eventCollector = new EventCollectorListener();
		FlowRunner runner = new FlowRunner(exFlow);
		runner.addListener(eventCollector);
		
		Assert.assertTrue(!runner.isCancelled());
		Assert.assertTrue(exFlow.getStatus() == Status.UNKNOWN);
		runner.run();
		Assert.assertTrue(exFlow.getStatus() == Status.SUCCEEDED);
		Assert.assertTrue(runner.getJobsFinished().size() == exFlow.getExecutableNodes().size());
		compareFinishedRuntime(runner);
		
		testStatus(exFlow, "job1", Status.SUCCEEDED);
		testStatus(exFlow, "job2", Status.SUCCEEDED);
		testStatus(exFlow, "job3", Status.SUCCEEDED);
		testStatus(exFlow, "job4", Status.SUCCEEDED);
		testStatus(exFlow, "job5", Status.SUCCEEDED);
		testStatus(exFlow, "job6", Status.SUCCEEDED);
		testStatus(exFlow, "job7", Status.SUCCEEDED);
		testStatus(exFlow, "job8", Status.SUCCEEDED);
		testStatus(exFlow, "job9", Status.SUCCEEDED);
		testStatus(exFlow, "job10", Status.SUCCEEDED);
		
		try {
			eventCollector.checkEventExists(new Type[] {Type.FLOW_STARTED, Type.FLOW_FINISHED});
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
			
			Assert.fail(e.getMessage());
		}
	}
	
	@Test
	public void exec1Failed() throws Exception {
		File testDir = new File("unit/executions/exectest1");
		ExecutableFlow exFlow = prepareExecDir(testDir, "exec2");
		
		EventCollectorListener eventCollector = new EventCollectorListener();
		FlowRunner runner = new FlowRunner(exFlow);
		runner.addListener(eventCollector);
		
		Assert.assertTrue(!runner.isCancelled());
		Assert.assertTrue(exFlow.getStatus() == Status.UNKNOWN);
		runner.run();
		
		Assert.assertTrue(exFlow.getStatus() == Status.FAILED);
		
		testStatus(exFlow, "job1", Status.SUCCEEDED);
		testStatus(exFlow, "job2d", Status.FAILED);
		testStatus(exFlow, "job3", Status.KILLED);
		testStatus(exFlow, "job4", Status.KILLED);
		testStatus(exFlow, "job5", Status.KILLED);
		testStatus(exFlow, "job6", Status.SUCCEEDED);
		testStatus(exFlow, "job7", Status.KILLED);
		testStatus(exFlow, "job8", Status.KILLED);
		testStatus(exFlow, "job9", Status.KILLED);
		testStatus(exFlow, "job10", Status.KILLED);

		try {
			eventCollector.checkEventExists(new Type[] {Type.FLOW_STARTED, Type.FLOW_FAILED_FINISHING, Type.FLOW_FINISHED});
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
			
			Assert.fail(e.getMessage());
		}
	}
	
	@Test
	public void exec1FailedKillAll() throws Exception {
		File testDir = new File("unit/executions/exectest1");
		ExecutableFlow exFlow = prepareExecDir(testDir, "exec2");
		exFlow.setFailureAction(FailureAction.CANCEL_ALL);

		EventCollectorListener eventCollector = new EventCollectorListener();
		FlowRunner runner = new FlowRunner(exFlow);
		runner.addListener(eventCollector);
		
		Assert.assertTrue(!runner.isCancelled());
		Assert.assertTrue(exFlow.getStatus() == Status.UNKNOWN);
		runner.run();
		
		Assert.assertTrue("Expected flow " + Status.FAILED + " instead " + exFlow.getStatus(), exFlow.getStatus() == Status.FAILED);
		
		testStatus(exFlow, "job1", Status.SUCCEEDED);
		testStatus(exFlow, "job2d", Status.FAILED);
		testStatus(exFlow, "job3", Status.KILLED);
		testStatus(exFlow, "job4", Status.KILLED);
		testStatus(exFlow, "job5", Status.KILLED);
		testStatus(exFlow, "job6", Status.FAILED);
		testStatus(exFlow, "job7", Status.KILLED);
		testStatus(exFlow, "job8", Status.KILLED);
		testStatus(exFlow, "job9", Status.KILLED);
		testStatus(exFlow, "job10", Status.KILLED);
		
		try {
			eventCollector.checkEventExists(new Type[] {Type.FLOW_STARTED, Type.FLOW_FAILED_FINISHING, Type.FLOW_FINISHED});
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
			eventCollector.writeAllEvents();
			Assert.fail(e.getMessage());
		}
	}
	
	private void testStatus(ExecutableFlow flow, String name, Status status) {
		ExecutableNode node = flow.getExecutableNode(name);
		
		if (node.getStatus() != status) {
			Assert.fail("Status of job " + node.getId() + " is " + node.getStatus() + " not " + status + " as expected.");
		}
	}
	
	private ExecutableFlow prepareExecDir(File execDir, String execName) throws IOException {
		FileUtils.copyDirectory(execDir, workingDir);
		
		File jsonFlowFile = new File(workingDir, execName + ".flow");
		HashMap<String, Object> flowObj = (HashMap<String, Object>) JSONUtils.parseJSONFromFile(jsonFlowFile);
		
		Flow flow = Flow.flowFromObject(flowObj);
		ExecutableFlow execFlow = new ExecutableFlow(execName, flow);
		execFlow.setExecutionPath(workingDir.getPath());
		return execFlow;
	}

	private void compareFinishedRuntime(FlowRunner runner) throws Exception {
		ExecutableFlow flow = runner.getFlow();
		for (String flowName: flow.getStartNodes()) {
			ExecutableNode node = flow.getExecutableNode(flowName);
			compareStartFinishTimes(flow, node, 0);
		}
	}
	
	private void compareStartFinishTimes(ExecutableFlow flow, ExecutableNode node, long previousEndTime) throws Exception {
		long startTime = node.getStartTime();
		long endTime = node.getEndTime();
		
		// If start time is < 0, so will the endtime.
		if (startTime <= 0) {
			Assert.assertTrue(endTime <=0);
			return;
		}
		
		System.out.println("Node " + node.getId() + " start:" + startTime + " end:" + endTime + " previous:" + previousEndTime);
		Assert.assertTrue("Checking start and end times", startTime > 0 && endTime >= startTime);
		Assert.assertTrue("Start time for " + node.getId() + " is " + startTime +" and less than " + previousEndTime, startTime >= previousEndTime);
		
		for (String outNode : node.getOutNodes()) {
			ExecutableNode childNode = flow.getExecutableNode(outNode);
			compareStartFinishTimes(flow, childNode, endTime);
		}
	}
}