package org.haibara.autoxm;

import io.socket.Hacker;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.json.JSONException;
import org.json.JSONObject;

public class XMRobot implements Runnable {

	protected String id = null;
	protected String pw = null;

	protected HttpClient client = null;

	protected SimpleDateFormat time = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");

	protected final String socketioHost = "sio.xiami.com:80";
	protected final String socketioHost_Alt = "sio.xiami.com:443";

	protected final String loginXMUrl = "http://www.xiami.com/member/login";
	protected final String xmHostHeader = "www.xiami.com";

	protected final String loginLoopUrl = "http://loop.xiami.com/member/login?done=/loop/prverify";
	protected final String loopHostHeader = "loop.xiami.com";

	protected final String userAgentHeader = "Mozilla/5.0 (Windows NT 6.1; rv:15.0) Gecko/20100101 Firefox/15.0.1";
	protected final String acceptCharsetHeader = "UTF-8,*;q=0.5";
	protected final String acceptEncodingHeader = "deflate";
	protected final String acceptLanguageHeader = "zh-cn,zh;q=0.8,en-us;q=0.5,en;q=0.3";
	protected final String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
	protected final String connectionHeader = "keep-alive";
	protected final String contentTypeHeader = "application/x-www-form-urlencoded";
	protected final String socketioHostHeader = "sio.xiami.com";

	private static final Pattern judgeReturnPattern = Pattern
			.compile("<strong>(.*?)</strong>");
	private static final Pattern commentIdPattern = Pattern.compile("<li id=\"(.*?)\"");
	// private static final String memberAuthKey = "member_auth";
	private static final String xiamiTokenKey = "_xiamitoken";

	protected String chatMessage = "";

	protected final String loopRoomUrl = "http://loop.xiami.com/room/";

	protected String defaultProtocol = "http";

	protected Pattern uidRe = Pattern
			.compile(
					"class=\"uico_home\"\\s+href=\"/u/([0-9]+)\"\\s+title=\".*?\\(.*?\\)\">",
					Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	protected Pattern nickRe = Pattern
			.compile(
					"class=\"uico_home\"\\s+href=\"/u/[0-9]+\"\\s+title=\".*?\\((.*?)\\)\">",
					Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	protected String uid = null;
	protected String nick = null;
	protected String xiamiToken = null;
	protected String parsedMemberAuth = null;
	protected SocketIO socket = null;
	protected int room = 0;
	protected boolean atRoom = false;
	protected boolean taskComplete = false;
	protected int showLog = -1;

	protected int rate = 0;
	protected int ratePeriod = 0;
	protected int chatPeriod = 0;
	protected boolean rateSignal = false;
	protected boolean chatSignal = false;
	protected LoopTaskThread chatThread = null;
	protected LoopTaskThread rateThread = null;

	protected List<String> taskList;
	protected Map<String, String> properties = null;

	public XMRobot(String id, String pw, Map<String, String> properties) {
		this.id = id;
		this.pw = pw;
		this.client = new DefaultHttpClient();
		this.properties = properties;
		try {
			this.room = Integer.parseInt(GetProperty("room"));
			this.showLog = Integer.parseInt(GetProperty("show_log"));
			this.rate = Integer.parseInt(GetProperty("rate"));
			this.ratePeriod = Integer.parseInt(GetProperty("rate_period")) <= 0 ? 30
					: Integer.parseInt(GetProperty("rate_period"));
			this.chatPeriod = Integer.parseInt(GetProperty("chat_period")) <= 0 ? 5
					: Integer.parseInt(GetProperty("chat_period"));
		} catch (NumberFormatException nfe) {
			// do nothing
		}
	}

	protected String GetProperty(String key) {
		if (this.properties == null || !this.properties.containsKey(key)) {
			return "";
		}
		return this.properties.get(key);
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
			System.err.println(this.id + " login xiami failed!");
			return false;
		}
		String cookie = response.getFirstHeader("Set-Cookie").toString();
		String authCookie = cookie.substring(
				cookie.indexOf(xiamiTokenKey + "=")
						+ (xiamiTokenKey + "=").length(), cookie.indexOf(";"));
		response.getEntity().consumeContent();
		this.xiamiToken = authCookie;
		this.parsedMemberAuth = this.xiamiToken.replace("%2B", "+").replace(
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
			System.err.println(this.id + " login loop failed!");
			return false;
		}
		String cookie = response.getFirstHeader("Set-Cookie").toString();
		String authCookie = cookie.substring(
				cookie.indexOf(xiamiTokenKey + "=")
						+ (xiamiTokenKey + "=").length(), cookie.indexOf(";"));
		this.xiamiToken = authCookie;
		this.parsedMemberAuth = this.xiamiToken.replace("%2B", "+").replace(
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
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken
				+ ";t_sign_auth=2;");
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.err.println(this.id + " visit xm failed!");
			return false;
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(response
				.getEntity().getContent(), "UTF-8"));
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

	public void MessageHandler(JSONObject json, IOAcknowledge ack) {
	}

	public void MessageHandler(String data, IOAcknowledge ack) {
		DEBUG("message:" + data);
	}

	public void ErrorHandler(SocketIOException socketIOException) {
		System.err.println(this.nick + " 掉线了！");
		// socketIOException.printStackTrace();
	}

	public void DisconnectHandler() {

	}

	public void ConnectHandler() {

	}

	public void EventHandler(String event, IOAcknowledge ack, Object... args) {
		DEBUG("event@" + uid + ":" + event);
	}

	public boolean enterLoopRoom(int roomId) throws IOException,
			InterruptedException {
		String roomUrl = defaultProtocol + "://" + socketioHost + "/room?id="
				+ roomId;
		if (this.xiamiToken == null || "".equals(xiamiToken)) {
			System.err.println("invalid " + xiamiTokenKey);
			return false;
		}
		HttpGet handshakeGet = new HttpGet();
		handshakeGet.addHeader("Host", socketioHostHeader);
		handshakeGet.addHeader("User-Agent", userAgentHeader);
		handshakeGet.addHeader("Accept", acceptHeader);
		handshakeGet.addHeader("Accept-Language", acceptLanguageHeader);
		handshakeGet.addHeader("Connection", connectionHeader);
		handshakeGet.addHeader("Referer", loopRoomUrl + roomId);
		handshakeGet.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		Hacker hacker = new Hacker(this.xiamiToken, roomId + "", client,
				handshakeGet, this.showLog);

		socket = new SocketIO(hacker);

		socket.connect(roomUrl, new IOCallback() {
			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {
				MessageHandler(json, ack);
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				MessageHandler(data, ack);
			}

			@Override
			public void onError(SocketIOException socketIOException) {
				ErrorHandler(socketIOException);
			}

			@Override
			public void onDisconnect() {
				DisconnectHandler();
			}

			@Override
			public void onConnect() {
				ConnectHandler();
			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {
				EventHandler(event, ack, args);
			}
		});
		return true;
	}

	public void stopWork() {

	}

	protected void DEBUG(String output) {
		if (showLog >= 1) {
			System.out.println("[DEBUG] " + output);
		}
	}

	public void startRateTask() {
		// good-or-bad task
		Map<String, String> gbParams = new HashMap<String, String>();
		gbParams.put("v", this.rate + "");
		gbParams.put("code", this.parsedMemberAuth);
		(new Thread(new LoopTaskThread(socket, this.ratePeriod * 1000,
				"GoodOrBad", gbParams, rateSignal))).start();
	}

	public void setChatPeriod(int period) {
		this.chatPeriod = period;
		if (this.chatThread != null) {
			this.chatThread.setPeriod(this.chatPeriod);
		}
	}

	public void setRatePeriod(int period) {
		this.ratePeriod = period;
		if (this.rateThread != null) {
			this.rateThread.setPeriod(this.ratePeriod);
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
		Map<String, String> chatParams = new HashMap<String, String>();
		chatParams.put("user_id", this.uid);
		chatParams.put("nick_name", this.nick);
		chatParams.put("code", this.parsedMemberAuth);
		chatParams.put("room_id", this.room + "");
		chatParams.put("msg", this.chatMessage);
		(new Thread(new LoopTaskThread(socket, this.chatPeriod * 1000,
				"CommonMsg", chatParams, chatSignal))).start();
		return true;
	}

	public List<String> judgeComment(String cid, String rate)
			throws ClientProtocolException, IOException {
		List<String> ret = new ArrayList<String>();
		int realId = Integer.parseInt(cid);
		rate = Integer.parseInt(rate) > 0 ? "1" : "2";
		HttpGet get = new HttpGet(
				"http://loop.xiami.com/commentlist/ageree/?id=" + realId
						+ "&state=" + rate + "&user_id=" + this.uid
						+ "&mode=ajax");
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept",
				"application/json, text/javascript, */*; q=0.01");
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("X-Requested-With", "XMLHttpRequest");
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken
				+ ";t_sign_auth=1;");
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.out.println(this.id + " judge " + rate + " to "
					+ cid + " failed");
			ret.add("fail");
			return ret;
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
		System.out.println(this.id + " judge " + rate + " to " + cid
				+ " success");
		Matcher m = judgeReturnPattern.matcher(pageString);
		if (m.find()) {
			System.out.println("detail:" + m.group(1));
			ret.add(m.group(1));
		} else {
			ret.add("success");
		}
		return ret;
	}

	public List<String> postCommentToSong(String songId, String comment)
			throws ClientProtocolException, IOException {
		List<String> ret = new ArrayList<String>();
		HttpGet get = new HttpGet(
				"http://www.xiami.com/commentlist/add?type=4&oid=" + songId
						+ "&content=" + URLEncoder.encode(comment,"UTF-8")
						+ "&relids=&mode=ajax&_xiamitoken=" + this.xiamiToken);
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept",
				"application/json, text/javascript, */*; q=0.01");
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("X-Requested-With", "XMLHttpRequest");
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Referer","http://"+xmHostHeader+"/song/"+songId);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken
				+ ";t_sign_auth=1;");
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.out.println(this.id + " post " + comment + " to "
					+ songId + " failed");
			ret.add("fail");
			return ret;
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
		try {
			JSONObject returnJson = new JSONObject(pageString);
			if (returnJson.get("status").equals("failed")) {
				System.out.println(this.id + " post " + comment + " to " + songId
					+ " failed");
				System.out.println("detail:"+returnJson.get("msg"));
			} else if (returnJson.get("status").equals("ok")) {
				System.out.println(this.id + " post " + comment + " to " + songId
						+ " success");
				Matcher m = commentIdPattern.matcher(returnJson.getString("output"));
				if (m.find()) {
					System.out.println("detail:"+m.group(1));
					ret.add(m.group(1));
				} else {
					System.out.println("detail:get comment id failed");					
				}
			}
		} catch (JSONException e) {
			System.out.println(this.id + " post " + comment + " to " + songId
					+ " failed");
			System.out.println("detail:"+pageString);
			e.printStackTrace();
		}

		return ret;
	}

	public List<String> payAttention(String uid)
			throws ClientProtocolException, IOException {
		List<String> ret = new ArrayList<String>();
		HttpGet get = new HttpGet("http://loop.xiami.com/member/attention/uid/"
				+ uid + "/format/json/type/1/from/loop");
		get.addHeader("Host", loopHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept", acceptHeader);
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken
				+ ";t_sign_auth=2;");
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.out.println(this.id + " pay attention to " + uid + " failed");
			ret.add("fail");
			return ret;
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
		System.out.println(this.id + " pay attention to " + uid + " success");
		ret.add("success");
		return ret;
	}

	public boolean updateUidNick() {
		try {
			if (this.loginLoop() && this.visitXMWithAuth() && this.uid != null
					&& this.nick != null) {
				if (DataHandler.getFileSize(XMDriver.root + XMDriver.profileDir
						+ this.uid) <= 0) {
					DEBUG("uid:" + this.uid + " updating");
					List<String> profile = new ArrayList<String>();
					profile.add("uid=" + this.uid);
					profile.add("nick=" + this.nick);
					profile.add("account=" + this.id);
					profile.add("password=" + this.pw);
					DataHandler.writeFile(profile, XMDriver.root
							+ XMDriver.profileDir + this.uid);
				} else {
					DEBUG("uid:" + this.uid + " updated");
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
		if (this.updateUidNick()) {
			DEBUG("uid:" + uid + ", nick:" + nick);
			try {
				this.enterLoopRoom(this.room);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			System.err.println(this.id + " update nick and uid failed");
		}
	}

	public void stopConnect() {
		if (this.socket != null) {
			this.socket.disconnect();
		}
	}
}
