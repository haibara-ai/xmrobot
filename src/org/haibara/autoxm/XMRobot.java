package org.haibara.autoxm;

import io.socket.Hacker;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
//import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.haibara.io.DataHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class XMRobot implements Runnable {

	private String id = null;
	private String pw = null;

	private HttpClient client = null;

	private SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private final String socketioHost = "sio.xiami.com:80";
	// private final String socketioHost_Alt = "sio.xiami.com:443";

	private final String loginXMUrl = "http://www.xiami.com/member/login";
	private final String xmHostHeader = "www.xiami.com";

	private final String loginLoopUrl = "http://loop.xiami.com/member/login?done=/loop/prverify";
	private final String loopHostHeader = "loop.xiami.com";

	// private final String visitLoopUrl = "http://loop.xiami.com/loop";
	// private final String visitXMUrl = "http://www.xiami.com/";
	private final String userAgentHeader = "Mozilla/5.0 (Windows NT 6.1; rv:15.0) Gecko/20100101 Firefox/15.0.1";
	private final String acceptCharsetHeader = "UTF-8,*;q=0.5";
	private final String acceptEncodingHeader = "deflate";
	private final String acceptLanguageHeader = "zh-cn,zh;q=0.8,en-us;q=0.5,en;q=0.3";
	private final String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
	private final String connectionHeader = "keep-alive";
	private final String contentTypeHeader = "application/x-www-form-urlencoded";
	private final String socketioHostHeader = "sio.xiami.com";

	private final String loopRoomUrl = "http://loop.xiami.com/room/";

	private int userCount = -1;

	private int maxLoopVal = 0;

	private String defaultProtocol = "http";

	private Pattern uidRe = Pattern
			.compile(
					"class=\"uico_home\"\\s+href=\"/u/([0-9]+)\"\\s+title=\".*?\\(.*?\\)\">",
					Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private Pattern nickRe = Pattern
			.compile(
					"class=\"uico_home\"\\s+href=\"/u/[0-9]+\"\\s+title=\".*?\\((.*?)\\)\">",
					Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private String uid = null;
	private String nick = null;
	private String memberAuth = null;
	private String parsedMemberAuth = null;

	SocketIO socket = null;
	private int room = 0;
	private int rate = 0;
	private int rate_period = 0;
	private int chat_period = 0;
	private String character = null;
	private int show_log = -1;

	private int loopVal = -1;

	private boolean atRoom = false;
	private boolean isDJ = false;

	private int startLoopVal = -1;
	private int playListCount = -1;

	private boolean taskComplete = false;
	private XMRobot cascadeRobot = null;
	private int auto_next_period = 0;

	public XMRobot(String id, String pw, String character, int auto_next_period, int chat_period,
			int rate_period, int rate, int room, int start_loop, int max_loop,
			int show_log, List<String> taskList) {
		client = new DefaultHttpClient();
		this.pw = pw;
		this.id = id;
		this.room = room;
		this.rate = rate;
		this.chat_period = chat_period;
		this.rate_period = rate_period;
		this.auto_next_period = auto_next_period;
		this.character = character;
		this.show_log = show_log;
		this.maxLoopVal = max_loop;
		this.startLoopVal = start_loop;
		this.taskList = taskList;
	}

	public void addCascadeRobot(XMRobot robot) {
		this.cascadeRobot = robot;
	}
	
	public boolean isDJ() {
		return this.isDJ;
	}
	
	@SuppressWarnings("deprecation")
	public boolean loginXM() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(loginXMUrl);
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		post.addHeader("Host", xmHostHeader);
		post.addHeader("User-Agent", userAgentHeader);
		post.addHeader("Accept", acceptHeader);
		post.addHeader("Accept-Language", acceptLanguageHeader);
		post.addHeader("Connection", connectionHeader);
		post.addHeader("Content-Type", contentTypeHeader);
		post.addHeader("Accept-Charset", acceptCharsetHeader);

		data.add(new BasicNameValuePair("email", id));
		data.add(new BasicNameValuePair("password", pw));
		data.add(new BasicNameValuePair("done", "/"));
		data.add(new BasicNameValuePair("submit", "登 录"));
		data.add(new BasicNameValuePair("type", ""));
		post.setEntity(new UrlEncodedFormEntity(data));

		HttpResponse response = client.execute(post);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 302) {
			System.err.println("login xiami failed!");
			return false;
		}
		String cookie = response.getFirstHeader("Set-Cookie").toString();
		String authCookie = cookie.substring(
				cookie.indexOf("member_auth=") + 12, cookie.indexOf(";"));
		response.getEntity().consumeContent();
		this.memberAuth = authCookie;
		this.parsedMemberAuth = this.memberAuth.replace("%2B", "+").replace(
				"%2F", "/");
		return true;
	}

	@SuppressWarnings("deprecation")
	public boolean loginLoop() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(loginLoopUrl);
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		post.addHeader("Host", loopHostHeader);
		post.addHeader("User-Agent", userAgentHeader);
		post.addHeader("Accept", acceptHeader);
		post.addHeader("Accept-Language", acceptLanguageHeader);
		post.addHeader("Connection", connectionHeader);
		post.addHeader("Content-Type", contentTypeHeader);
		post.addHeader("Accept-Charset", acceptCharsetHeader);

		data.add(new BasicNameValuePair("email", id));
		data.add(new BasicNameValuePair("password", pw));
		data.add(new BasicNameValuePair("submit", "+"));
		post.setEntity(new UrlEncodedFormEntity(data));

		HttpResponse response = client.execute(post);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 302) {
			System.err.println("login loop failed!");
			return false;
		}
		String cookie = response.getFirstHeader("Set-Cookie").toString();
		String authCookie = cookie.substring(
				cookie.indexOf("member_auth=") + 12, cookie.indexOf(";"));
		this.memberAuth = authCookie;
		this.parsedMemberAuth = this.memberAuth.replace("%2B", "+").replace(
				"%2F", "/");
		response.getEntity().consumeContent();
		return true;
	}

	public boolean visitXMWithAuth() throws ClientProtocolException,
			IOException {
		HttpGet get = new HttpGet("http://www.xiami.com/");
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept", acceptHeader);
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Referer", "http://www.xiami.com/");
		get.addHeader("Cookie", "member_auth=" + this.memberAuth
				+ ";t_sign_auth=2;");
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.err.println("visit xm failed!");
			return false;
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(response
				.getEntity().getContent()));
		String line = "";
		StringBuffer page = new StringBuffer();
		while ((line = br.readLine()) != null) {
			page.append(line);
		}
		String pageString = page.toString();
		Matcher uidM = uidRe.matcher(pageString);
		if (uidM.find()) {
			this.uid = uidM.group(1);
		}
		Matcher nickM = nickRe.matcher(pageString);
		if (nickM.find()) {
			this.nick = nickM.group(1);
		}
		return true;
	}

	public boolean enterLoopRoom(String roomId) throws IOException,
			InterruptedException {
		String roomUrl = defaultProtocol + "://" + socketioHost + "/room?id="
				+ roomId;
		if (this.memberAuth == null || "".equals(memberAuth)) {
			System.err.println("invalid member_auth");
			return false;
		}
		HttpGet handshakeGet = new HttpGet();
		handshakeGet.addHeader("Host", socketioHostHeader);
		handshakeGet.addHeader("User-Agent", userAgentHeader);
		handshakeGet.addHeader("Accept", acceptHeader);
		handshakeGet.addHeader("Accept-Language", acceptLanguageHeader);
		handshakeGet.addHeader("Connection", connectionHeader);
		handshakeGet.addHeader("Referer", loopRoomUrl + roomId);
		handshakeGet.addHeader("Cookie", "member_auth=" + this.memberAuth);
		Hacker hacker = new Hacker(this.memberAuth, roomId, client,
				handshakeGet, this.show_log);

		socket = new SocketIO(hacker);

		socket.connect(roomUrl, new IOCallback() {
			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {

			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				DEBUG("message:" + data);
			}

			@Override
			public void onError(SocketIOException socketIOException) {
				socketIOException.printStackTrace();
				if (!"dj".equals(character)) {
					return;
				}
				logLoopVal(loopVal);
			}

			@Override
			public void onDisconnect() {
				if (!"dj".equals(character)) {
					return;
				}
				atRoom = false;
				logLoopVal(loopVal);
				if (taskComplete && cascadeRobot != null) {
					new Thread(cascadeRobot).start();
				}
			}

			@Override
			public void onConnect() {
			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {
				DEBUG("event@" + uid + ":" + event);
				if (!"dj".equals(character)) {
					return;
				}
				if ("LeaveRoom".equals(event)) {
					try {
						JSONArray json = new JSONArray(args);
						JSONObject job = json.getJSONObject(0);
						DEBUG(job.toString());
						JSONArray userArray = job.getJSONArray("users");
						userCount = userArray.length();
						DEBUG("userCount:" + userCount);
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				if ("EnterRoom".equals(event)) {
					try {
						JSONArray json = new JSONArray(args);
						JSONObject job = json.getJSONObject(0);
						JSONObject roomO = job.getJSONObject("room");
						JSONArray userArray = roomO.getJSONArray("users");
						userCount = userArray.length();
						DEBUG("userCount:" + userCount);
						for (int i = 0; i < userArray.length(); i++) {
							JSONObject userO = userArray.getJSONObject(i);
							if (uid.equals(userO.getString("user_id"))) {
								atRoom = true;
								if (startLoopVal == -1) {
									startLoopVal = userO.getInt("loop");
									logLoopVal(startLoopVal);
								}
								loopVal = userO.getInt("loop");
								if (loopVal - startLoopVal >= maxLoopVal) {
									taskComplete = true;
									logLoopVal(loopVal);
									leaveDJ();
								}
								try {
									playListCount = userO.getString("playlist")
											.length() == 0 ? 0
											: userO.getString("playlist")
													.split(",").length;
								} catch (JSONException e1) {
									playListCount = 0;
								}
								if (playListCount <= 0) {
									addSongToPlaylist("1768969254");
								} else if (!isDJ) {
									setDJ();
								}
								break;
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				if ("AddPlaylist".equals(event)) {
					playListCount += 1;
					if (atRoom && !isDJ && "dj".equals(character)) {
						setDJ();
					}
				}
				if ("SetDJ".equals(event)) {
					try {
						JSONArray json = new JSONArray(args);
						JSONObject job = json.getJSONObject(0);
						if (job.getString("user_id").equals(uid)) {
							isDJ = true;
							if (playSignal) {
								startAutoPlayTask();
							} else {
								playNext();
							}
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				if ("LeaveDJ".equals(event)) {
					isDJ = false;
					socket.disconnect();
				}

				if ("UpdateUser".equals(event)) {
					try {
						JSONArray json = new JSONArray(args);
						JSONObject job = json.getJSONObject(0);
						DEBUG("update " + uid);
						if (job.getString("user_id").equals(uid)) {
							loopVal = job.getInt("loop");
							DEBUG("loop val : " + loopVal);
							if (loopVal - startLoopVal >= maxLoopVal) {
								taskComplete = true;
								logLoopVal(loopVal);
								leaveDJ();
							}
						} else {
							// do nothing
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
		});
		return true;
	}

	public void stopWork() {
		
	}
	
	private void DEBUG(String output) {
		if (show_log >= 1) {
			System.out.println("[DEBUG] " + output);
		}
	}

	private void logLoopVal(int loopVal) {
		try {
			if (loopVal != -1) {
				DataHandler.appendFile(time.format(new Date()) + "=" + loopVal
						+ "\n", XMDriver.root+"/config/log/." + uid);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean leaveDJ() {
		Map<String, String> jsonMap = new HashMap<String, String>();
		jsonMap.put("user_id", this.uid);
		socket.emit("LeaveDJ", jsonMap);
		return true;
	}

	public boolean addSongToPlaylist(String songId) {
		Map<String, String> jsonMap = new HashMap<String, String>();
		jsonMap.put("song_id", songId);
		socket.emit("AddPlaylist", jsonMap);
		return true;
	}

	public boolean setDJ() {
		Map<String, String> setDJMap = new HashMap<String, String>();
		setDJMap.put("user_id", this.uid);
		setDJMap.put("nick_name", this.nick);
		setDJMap.put("room_id", this.room + "");
		setDJMap.put("code", this.parsedMemberAuth);
		this.socket.emit("SetDJ", setDJMap);
		return true;
	}
	
	public boolean playNext() {
		System.out.println("startLoop:"+startLoopVal);
		Map<String, String> jsonMap = new HashMap<String, String>();
		jsonMap.put("user_id", this.uid);
		jsonMap.put("room_id", this.room + "");
		jsonMap.put("code", this.parsedMemberAuth);
		DEBUG("userCount:" + userCount + " startLoop:" + startLoopVal
				+ " curLoop:" + loopVal);
		this.socket.emit("PlayNext", jsonMap);
		return true;
	}
	
	private boolean playSignal = false;
	
	public void stopAutoPlay() {
		if (this.playSignal == true) {
			this.playSignal = false;
			if (this.playThread != null) {
				this.playThread.setSignal(this.chatSignal);
			}
		}
	}
	
	public boolean startAutoPlayTask() {
		System.out.println("startLoop:"+startLoopVal);
		Map<String, String> jsonMap = new HashMap<String, String>();
		jsonMap.put("user_id", this.uid);
		jsonMap.put("room_id", this.room + "");
		jsonMap.put("code", this.parsedMemberAuth);		
		playThread = new XMTaskThread(socket,
				this.auto_next_period * 1000, "PlayNext", jsonMap, playSignal);
		new Thread(playThread).start();
		return true;
	}
	
	private boolean rateSignal = false;
	
	public void startRateTask() {
		// good-or-bad task
		if (this.rate == 0) {
			this.rateSignal = false;
			return;
		}
		Map<String, String> gbParams = new HashMap<String, String>();
		gbParams.put("v", this.rate + "");
		gbParams.put("code", this.parsedMemberAuth);
		rateThread = new XMTaskThread(socket, this.rate_period * 1000,
				"GoodOrBad", gbParams, rateSignal);
		new Thread(rateThread).start();
	}

	private String chatMessage = "";
	
	public void setChatPeriod(int period) {
		this.chat_period = period;
		if (this.chatThread != null) {
			this.chatThread.setPeriod(this.chat_period);
		}
	}
	public void setRatePeriod(int period) {
		this.rate_period = period;
		if (this.rateThread != null) {
			this.rateThread.setPeriod(this.rate_period);
		}
	}
	
	public void setPlayPeriod(int period) {
		this.auto_next_period = period;
		if (this.playThread != null) {
			this.playThread.setPeriod(this.auto_next_period);
		}
	}
	
	public void setChatMessage(String chatMessage) {
		this.chatMessage = chatMessage;
		if (this.chatSignal == false) {
			this.chatSignal = true;
			this.startChatTask();
		}
	}
	
	public void stopChat() {
		if (this.chatSignal == true) {
			this.chatSignal = false;
			if (this.chatThread != null) {
				this.chatThread.setSignal(this.chatSignal);
			}
		}
	}
	
	private XMTaskThread chatThread = null;
	private XMTaskThread rateThread = null;
	private XMTaskThread playThread = null;
	
	public void stopRate() {
		if (this.rateSignal == true) {
			this.rateSignal = false;
			if (this.rateThread != null) {
				this.rateThread.setSignal(this.rateSignal);
			}
		}
	}
	
	public void setRate(int rate) {
		this.rate = rate;
		if (this.rateSignal == false) {
			this.rateSignal = true;
			this.startRateTask();
		}
	}
	
	public boolean startChatTask() {
		// chat task
		if (this.chatMessage == null || this.chatMessage.length() == 0) {
			this.chatSignal = false;
			return false;
		}
		Map<String, String> chatParams = new HashMap<String, String>();
		chatParams.put("user_id", this.uid);
		chatParams.put("nick_name", this.nick);
		chatParams.put("code", this.parsedMemberAuth);
		chatParams.put("room_id", this.room + "");
		chatParams.put("msg", this.chatMessage);
		chatThread = new XMTaskThread(socket,
				this.chat_period * 1000, "CommonMsg", chatParams, chatSignal);
		new Thread(chatThread).start();
		return true;
	}

	public boolean payAttention(String uid) throws ClientProtocolException,
			IOException {
		HttpGet get = new HttpGet("http://loop.xiami.com/member/attention/uid/"
				+ uid + "/format/json/type/1/from/loop");
		get.addHeader("Host", loopHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept", acceptHeader);
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Cookie", "member_auth=" + this.memberAuth
				+ ";t_sign_auth=2;");
		System.out.println(get.getRequestLine());
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.err.println("pay attention fail!");
			return false;
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(response
				.getEntity().getContent()));
		String line = "";
		StringBuffer page = new StringBuffer();
		while ((line = br.readLine()) != null) {
			page.append(line);
		}
		String pageString = page.toString();
		DEBUG(pageString);
		return true;
	}

	public boolean updateUidNick() {
		try {
			if (this.loginLoop() && this.visitXMWithAuth() && this.uid != null
					&& this.nick != null) {
				if (DataHandler.getFileSize(XMDriver.root+"/config/profile/" + this.uid) <= 0) {
					DEBUG("uid:" + this.uid + " updating");
					List<String> profile = new ArrayList<String>();
					profile.add("uid=" + this.uid);
					profile.add("nick=" + this.nick);
					profile.add("account=" + this.id);
					profile.add("password=" + this.pw);
					DataHandler.writeFile(profile, XMDriver.root+"/config/profile/"
							+ this.uid);
				} else {
					DEBUG("uid:" + this.uid + " updated.");
				}
				return true;
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public void run() {
		System.out.println("start " + character + " : " + this.id);
		if (this.updateUidNick()) {
			DEBUG("uid:" + uid + ", nick:" + nick);
			try {
				this.enterLoopRoom(this.room + "");
				for (String task : this.taskList) {
					switch (task) {
					case "rate":
						this.rateSignal = true;
						this.startRateTask();
						break;
					case "chat":
						this.chatSignal = true;
						this.startChatTask();
						break;
					case "auto_next":
						this.playSignal = true;
						break;
					default:
						break;
					}
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("uid:"+uid+" update nick and uid fail.");
		}
	}

	private List<String> taskList;
	private boolean chatSignal = false;
	
	private class XMTaskThread implements Runnable {
		private SocketIO socket = null;
		private long interval = Integer.MAX_VALUE;
		private String event = null;
		private Map<String, String> jsonParams;
		private boolean signal = false;
		XMTaskThread(SocketIO socket, int interval, String event,
				Map<String, String> jsonParams, boolean signal) {
			this.socket = socket;
			this.interval = interval <= 1000 ? 1000 : interval;
			this.event = event;
			this.jsonParams = jsonParams;
			this.signal = signal;
		}

		@Override
		public void run() {
			while (signal == true && this.socket != null) {
				this.socket.emit(event, jsonParams);
				try {
					Thread.sleep(interval);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		public void setSignal(boolean signal) {
			this.signal = signal;
		}
		public void setPeriod(int period) {
			this.interval = period;
		}
	}
}
