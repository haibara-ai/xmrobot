package org.haibara.autoxm;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;

import org.haibara.io.DataHandler;

public class XMDriver {

	static String root = "";

	@SuppressWarnings("unchecked")
	private static Map<String, List<Map<String, String>>> loadUser(String path) {
		Map<String, List<Map<String, String>>> ret = new HashMap<String, List<Map<String, String>>>();
		try {
			String curMode = "null";
			ret.put(curMode, new ArrayList<Map<String, String>>());
			List<String> content = (List<String>) DataHandler.readFile(path,
					"list");
			for (String line : content) {
				String piece = line.trim();
				if (piece.isEmpty() || piece.startsWith("#")) {
					continue;
				}
				if ("dj".equals(piece.toLowerCase())) {
					curMode = "dj";
					ret.put(curMode, new ArrayList<Map<String, String>>());
					continue;
				} else if ("audience".equals(piece.toLowerCase())) {
					curMode = "audience";
					ret.put(curMode, new ArrayList<Map<String, String>>());
					continue;
				}
				String[] elements = piece.split(",");
				if (elements.length != 2) {
					System.err.println("invalid element format:" + piece);
					continue;
				}
				Map<String, String> temp = new HashMap<String, String>();
				for (String element : elements) {
					String[] kv = element.trim().split("=");
					if (kv.length != 2) {
						System.err.println("invalid kv format:" + element);
						continue;
					}
					temp.put(kv[0], kv[1]);
				}
				ret.get(curMode).add(temp);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	/*
	 * 读取setting.txt文件，#行为注释，跳过
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> loadSetting(String path) {
		List<String> content = new ArrayList<String>();
		try {
			content = (List<String>) DataHandler.readFile(path, "list");
		} catch (Exception e) {
			System.err.println("Load setting.txt error");
			e.printStackTrace();
		}
		Map<String, String> ret = new HashMap<String, String>();
		for (String line : content) {
			String piece = line.trim();
			if (piece.isEmpty() || piece.startsWith("#")) {
				continue;
			}
			String[] kv = piece.split("=");
			if (kv.length != 2) {
				System.err.println("invalid element format:" + piece);
				continue;
			}
			ret.put(kv[0], kv[1]);
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> loadProfile(String dir) {
		Map<String, String> ret = new HashMap<String, String>();
		for (String file : DataHandler.listFileName(dir)) {
			try {
				List<String> profile = (List<String>) DataHandler.readFile(root
						+ "/config/profile/" + file, "list");
				for (String line : profile) {
					if (line.startsWith("account=")) {
						ret.put(line.substring(line.indexOf("=") + 1), file);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ret;
	}

	private static SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd");

	@SuppressWarnings({ "unchecked" })
	private static int loadLoopLog(String filename, int maxLoop) {
		if (DataHandler.getFileSize(filename) <= 0) {
			return -1;
		}
		try {
			List<String> content = (List<String>) DataHandler.readFile(
					filename, "list");
			String lastDay = content.get(content.size() - 1).substring(0,
					content.get(content.size() - 1).indexOf(" "));
			String curDay = day.format(new Date());
			if (curDay.equals(lastDay)) {
				String lastLine = "";
				int lastLoop = -1;
				for (int i = content.size() - 1; i >= 0; i--) {
					if (content.get(i).startsWith(curDay)) {
						lastLine = content.get(i);
						if (i == content.size() - 1) {
							lastLoop = Integer.parseInt(lastLine
									.substring(lastLine.indexOf("=") + 1));
						}
						if (i == 0) {
							int startLoop = Integer.parseInt(lastLine
									.substring(lastLine.indexOf("=") + 1));
							if (lastLoop - startLoop >= maxLoop) {
								return Integer.MAX_VALUE;
							} else {
								return startLoop;
							}
						}
					} else {
						calendar.setTime(new Date());
						calendar.add(Calendar.DATE, -1);
						String yesterday = day.format(calendar.getTime());
						if (content.get(i).startsWith(yesterday)) {
							int startLoop = Integer.parseInt(content.get(i)
									.substring(lastLine.indexOf("=") + 1));
							if (lastLoop - startLoop >= maxLoop) {
								return Integer.MAX_VALUE;
							} else {
								return startLoop;
							}
						} else {
							int startLoop = Integer.parseInt(content.get(i+1).substring(
									lastLine.indexOf("=") + 1));
							if (lastLoop - startLoop >= maxLoop) {
								return Integer.MAX_VALUE;
							} else {
								return startLoop;
							}
						}
					}
				}
			} else {
				return -1;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	private static Calendar calendar = new GregorianCalendar();

	public void setAudienceChat(String message) {
		for (XMRobot robot : audienceList) {
			if (robot != null) {
				robot.setChatMessage(message);
			}
		}
	}
	public void setAudienceChatPeriod(int period) {
		for (XMRobot robot : audienceList) {
			if (robot != null) {
				robot.setChatPeriod(period*1000);
			}
		}
	}
	public void setAudienceRatePeriod(int period) {
		for (XMRobot robot : audienceList) {
			if (robot != null) {
				robot.setRatePeriod(period*1000);
			}
		}
	}
	public void setAudienceRate(int rate) {
		for (XMRobot robot : audienceList) {
			if (robot != null) {
				robot.setRate(rate);
			}
		}
	}
	
	
	public void stopAudienceChat() {
		for (XMRobot robot : audienceList) {
			if (robot != null) {
				robot.stopChat();
			}
		}
	}

	int dj_count = 0;
	int dj_chat_period = 0;
	int dj_rate_period = 0;
	int dj_auto_next_period = 0;
	int dj_rate = 0;
	int dj_room = 0;
	int max_loop = 0;

	int audience_count = 0;
	int audience_chat_period = 0;
	int audience_rate_period = 0;
	int audience_rate = 0;
	int audience_room = 0;

	int show_dj_log = 0;
	int show_audience_log = 0;

	int mode = -1;

	private List<XMRobot> djList = new ArrayList<XMRobot>();
	private List<XMRobot> audienceList = new ArrayList<XMRobot>();

	public void start() throws InterruptedException {
		Map<String, List<Map<String, String>>> users = XMDriver.loadUser(root
				+ "/config/user.txt");
		Map<String, String> settings = XMDriver.loadSetting(root
				+ "/config/setting.txt");
		Map<String, String> profiles = XMDriver.loadProfile(root
				+ "/config/profile/");
		mode = Integer.parseInt(settings.get("mode"));
		show_dj_log = Integer.parseInt(settings.get("show_dj_log"));
		show_audience_log = Integer.parseInt(settings.get("show_audience_log"));

		if (mode == 0) {
			dj_count = users.get("dj").size();
			audience_count = users.get("audience").size();
		} else if (mode == 1) {
			dj_count = Integer.parseInt(settings.get("dj_count")) > users.get(
					"dj").size() ? users.get("dj").size() : Integer
					.parseInt(settings.get("dj_count"));
			dj_chat_period = Integer.parseInt(settings.get("dj_chat_period"));
			dj_rate_period = Integer.parseInt(settings.get("dj_rate_period"));
			dj_auto_next_period = Integer.parseInt(settings
					.get("auto_next_period"));
			dj_rate = Integer.parseInt(settings.get("dj_rate"));
			dj_room = Integer.parseInt(settings.get("dj_room"));
			max_loop = Integer.parseInt(settings.get("dj_max_loop"));
		} else if (mode == 2) {
			audience_count = Integer.parseInt(settings.get("audience_count")) > users
					.get("audience").size() ? users.get("audience").size()
					: Integer.parseInt(settings.get("audience_count"));
			audience_chat_period = Integer.parseInt(settings
					.get("audience_chat_period"));
			audience_rate_period = Integer.parseInt(settings
					.get("audience_rate_period"));
			audience_rate = Integer.parseInt(settings.get("audience_rate"));
			audience_room = Integer.parseInt(settings.get("audience_room"));
			max_loop = Integer.parseInt(settings.get("dj_max_loop"));
		} else if (mode == 3) {
			dj_count = Integer.parseInt(settings.get("dj_count")) > users.get(
					"dj").size() ? users.get("dj").size() : Integer
					.parseInt(settings.get("dj_count"));
			dj_chat_period = Integer.parseInt(settings.get("dj_chat_period"));
			dj_rate_period = Integer.parseInt(settings.get("dj_rate_period"));
			dj_auto_next_period = Integer.parseInt(settings
					.get("auto_next_period"));
			dj_rate = Integer.parseInt(settings.get("dj_rate"));
			dj_room = Integer.parseInt(settings.get("dj_room"));
			audience_count = Integer.parseInt(settings.get("audience_count")) > users
					.get("audience").size() ? users.get("audience").size()
					: Integer.parseInt(settings.get("audience_count"));
			audience_chat_period = Integer.parseInt(settings
					.get("audience_chat_period"));
			audience_rate_period = Integer.parseInt(settings
					.get("audience_rate_period"));
			audience_rate = Integer.parseInt(settings.get("audience_rate"));
			audience_room = Integer.parseInt(settings.get("audience_room"));
			max_loop = Integer.parseInt(settings.get("dj_max_loop"));
		} else {
			System.err.println("Invalid mode:" + mode);
		}

		System.out.println("dj count:" + dj_count);
		System.out.println("audience count:" + audience_count);
		// start dj
		int dj_index = 0;
		List<String> djTaskList = new ArrayList<String>();
		if (dj_auto_next_period > 0) {
			djTaskList.add("auto_next");
		}
		for (Map<String, String> user : users.get("dj")) {
			if (dj_index >= dj_count) {
				break;
			}
			int startLoopVal = loadLoopLog(
					root + "/config/log/." + profiles.get(user.get("user")),
					max_loop);
			if (startLoopVal == Integer.MAX_VALUE) {
				System.out.println(profiles.get(user.get("user"))
						+ " task completed");
				continue;
			}
			XMRobot robot = new XMRobot(user.get("user"), user.get("password"),
					"dj", dj_auto_next_period, dj_chat_period, dj_rate_period,
					dj_rate, dj_room, startLoopVal, max_loop, show_dj_log,
					djTaskList);
			dj_index++;
			djList.add(robot);
		}
		for (int i = 0; i < djList.size() - 1; i++) {
			djList.get(i).addCascadeRobot(djList.get(i + 1));
		}
		if (djList.size() > 0) {
			new Thread(djList.get(0)).start();
		}
		// start audience
		int audience_index = 0;
		List<String> audienceTaskList = new ArrayList<String>();
		audienceTaskList.add("chat");
		audienceTaskList.add("rate");
		for (Map<String, String> user : users.get("audience")) {
			if (audience_index >= audience_count) {
				break;
			}
			int startLoopVal = -1;
			XMRobot robot = new XMRobot(user.get("user"), user.get("password"),
					"audience", dj_auto_next_period, audience_chat_period,
					audience_rate_period, audience_rate, audience_room,
					startLoopVal, max_loop, show_audience_log, audienceTaskList);
			audienceList.add(robot);
			new Thread(robot).start();
			audience_index++;
			Thread.sleep(Integer.parseInt(settings.get("login_gap")));
		}
	}
	
	public XMRobot getCurrentDJ() {
		for (XMRobot dj: djList) {
			if (dj != null && dj.isDJ()) {
				return dj;
			}
		}
		return null;
	}
	
	public void stopALLDJ() {
		for (XMRobot dj : djList) {
			if (dj != null) {
				dj.stopWork();
			}
		}
	}
	
	public void stopALLAudience() {
		for (XMRobot robot : audienceList) {
			if (robot != null) {
				robot.stopWork();
			}
		}
	}
}
