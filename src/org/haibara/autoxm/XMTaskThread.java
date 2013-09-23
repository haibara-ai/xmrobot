package org.haibara.autoxm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XMTaskThread implements Runnable {

	private Map<String,Method> methodMap;
	private List<List<String>> todoList;
	private Map<String, List<String>> invokerMap;
	private Map<String, List<String[]>> methodParamsMap;
	private Map<String, String> namePwMap;
	private Map<String, XMRobot> robotPool = new HashMap<String, XMRobot>();

	XMTaskThread(Map<String,Method> methodMap, List<List<String>> todoList,
			Map<String, List<String>> invokerMap,
			Map<String, List<String[]>> methodParamsMap,
			Map<String, String> namePwMap) {
		this.methodMap = methodMap;
		this.todoList = todoList;
		this.invokerMap = invokerMap;
		this.methodParamsMap = methodParamsMap;
		this.namePwMap = namePwMap;
	}

	private void cascadeWork(int stage, List<String> lastStageOutput) {
		List<String> curStageMethods = todoList.get(stage);
		for (String curMethodStr : curStageMethods) {			
			List<String[]> methodParamsList = methodParamsMap.get(curMethodStr);
			List<String> invokerList = invokerMap.get(curMethodStr);
			Method curMethod = methodMap.get(curMethodStr);		
			int lastSOCounter = 0;
			for (int i = 0; i < invokerList.size(); i++) {
				if ("[stdin]".equals(invokerList.get(i))) {
					invokerList.set(i, lastStageOutput.get(lastSOCounter));
					lastSOCounter++;
				}
			}
			for (String[] omp : methodParamsList) {
				String[] methodParams = omp.clone();
				// refine methodParams
				if (methodParams == null || methodParams.length == 0) {

				} else {

					for (int i = 0; i < methodParams.length; i++) {
						if ("[stdin]".equals(methodParams[i])) {
							if (lastStageOutput != null
									&& lastSOCounter < lastStageOutput.size()) {
								methodParams[i] = lastStageOutput
										.get(lastSOCounter);
								lastSOCounter++;
							} else {
								throw new RuntimeException(
										"error action config: last stage output count:"
												+ (lastStageOutput == null ? 0
														: lastStageOutput
																.size())
												+ ",index:" + lastSOCounter);
							}
						}
					}
				}
				for (String robotName : invokerList) {
					if (!robotPool.containsKey(robotName)) {
						robotPool.put(robotName, new XMRobot(robotName,
								namePwMap.get(robotName), null));
					}
					XMRobot robot = robotPool.get(robotName);
					try {
						@SuppressWarnings("unchecked")
						List<String> stageOutput = ((List<String>) curMethod
								.invoke(robot, ((Object[]) methodParams)));
						if (stageOutput.contains("fail")) {
							System.err.println(robot.id + " "
									+ curMethod.toString() + " failed");
						} else {
							if (stage < todoList.size() - 1) {
								cascadeWork(stage + 1, stageOutput);
							} else {
								// work flow end
							}
						}
					} catch (IllegalAccessException | IllegalArgumentException
							| InvocationTargetException e) {
						e.printStackTrace();
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	@Override
	public void run() {
		cascadeWork(0, null);
	}
}
