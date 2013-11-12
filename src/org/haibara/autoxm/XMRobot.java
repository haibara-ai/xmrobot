package org.haibara.autoxm;

import io.socket.Hacker;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

public class XMRobot implements Runnable {

	protected String id = null;
	protected String pw = null;

	protected HttpClient client = null;
	protected boolean online = false;

	protected SimpleDateFormat time = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");

	protected final String socketioHost = "sio.xiami.com:80";
	protected final String socketioHost_Alt = "sio.xiami.com:443";

	protected final String loginXMUrl = "http://www.xiami.com/member/login";
	protected final String logoutXMUrl = "http://www.xiami.com/member/logout";
	protected final String xmHostHeader = "www.xiami.com";

	protected final String loginLoopUrl = "http://loop.xiami.com/member/login?done=/loop/prverify";
	protected final String loopHostHeader = "loop.xiami.com";

	protected final String userAgentHeader = "Mozilla/5.0 (Windows NT 6.1; rv:2.0) Gecko/20100101 Firefox/4.0 QQDownload/1.7";
	protected final String acceptCharsetHeader = "UTF-8,*;q=0.5";
	protected final String acceptEncodingHeader = "deflate";
	protected final String acceptLanguageHeader = "zh-cn,zh;q=0.8,en-us;q=0.5,en;q=0.3";
	protected final String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
	protected final String connectionHeader = "keep-alive";
	protected final String contentTypeHeader = "application/x-www-form-urlencoded";
	protected final String socketioHostHeader = "sio.xiami.com";
	
	private static Map<Integer,String> objectTypeName = new HashMap<Integer, String>();
	
	static {
		objectTypeName.put(1, "album");
		objectTypeName.put(3, "artist");
		objectTypeName.put(4, "song");
	}
	
	private static final Pattern judgeReturnPattern = Pattern
			.compile("<strong>(.*?)</strong>");
	private static final Pattern commentIdPattern = Pattern
			.compile("<li id=\"(.*?)\"");
	private static final Pattern replyIdPattern = Pattern
			.compile("\"brief_(.*?)\"");
	private static final String memberAuthKey = "member_auth";
	private static final String xiamiTokenKey = "_xiamitoken";

	protected String chatMessage = "";

	protected final String loopRoomUrl = "http://loop.xiami.com/room/";

	protected String defaultProtocol = "http";
	protected Pattern uidRe = Pattern.compile("/web/feed/id/(.*?)\"");

	protected Pattern nickRe = Pattern.compile("<b>(.*?)</b>");

	protected String uid = null;
	protected String nick = null;
	protected String xiamiToken = null;
	protected String parsedXiamiToken = null;
	protected String parsedMemberAuth = null;
	protected String rawMemberAuth = null;
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
	
	public String visitPage(String url) throws ClientProtocolException, IOException {
		url = url.trim().replace("http[s]://", "");
		HttpPost get = new HttpPost("http://"+url);
		get.addHeader("Host", url.substring(0,url.indexOf("/")));
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept", acceptHeader);
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Charset", acceptCharsetHeader);
		if (this.xiamiToken != null) {
			get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		}
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		String pageString = getResponseContent(response);
		if (statusCode != 200) {
			System.err.println(this.id + " visit "+url+" failed:" + statusCode);
			System.out.println("detail:"+pageString);
			return null;
		}
		System.err.println(this.id + " visit "+url+" success");
		return pageString;
		
	}
	
	private Pattern tokenInputPattern = Pattern.compile("name=\"token\"\\s+value=\"(.*?)\"");
	private Pattern xiamiTokenInputPattern = Pattern.compile("name=\"_xiamitoken\"\\s+value=\"(.*?)\"");
	public List<String> registXM(String email,String nick,String password,String checkcode) throws ClientProtocolException, IOException {				
		String pageContent = this.visitPage("www.xiami.com/member/register");
		if (pageContent == null) {
			System.err.println("regist XM error: email="+email);
			System.err.println("detail:"+"get regist page error");
			return null;
		}
		Matcher m = tokenInputPattern.matcher(pageContent);
		String token = "";
		if (m.find()) {
			token = m.group(1);
		} else {
			System.err.println("regist XM error: email="+email);
			System.err.println("detail:"+"get token error");
			return null;
		}
		m = xiamiTokenInputPattern.matcher(pageContent);
		String xiamiTokenInput = "";
		if (m.find()) {
			xiamiTokenInput = m.group(1);
		}
		
		HttpPost post = new HttpPost("http://www.xiami.com/member/register");
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		post.addHeader("Host", xmHostHeader);
		post.addHeader("User-Agent", userAgentHeader);
		post.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		post.addHeader("Accept-Language", acceptLanguageHeader);
		post.addHeader("Connection", connectionHeader);
		post.addHeader("Referer", "http://www.xiami.com/member/register");		
		post.addHeader("Content-Type", "application/x-www-form-urlencoded");
		post.addHeader("Accept-Charset", acceptCharsetHeader);

		data.add(new BasicNameValuePair("email", email));
		data.add(new BasicNameValuePair("nickname", nick));
		data.add(new BasicNameValuePair("password", password));
		data.add(new BasicNameValuePair("validate", checkcode));
		data.add(new BasicNameValuePair("invite_code", ""));
		data.add(new BasicNameValuePair("agreement", "on"));
		data.add(new BasicNameValuePair("submit", "11"));
		data.add(new BasicNameValuePair("target", ""));
		data.add(new BasicNameValuePair("token", token));
		if (xiamiTokenInput.length() > 0) {
			data.add(new BasicNameValuePair("_xiamitoken", xiamiTokenInput));				
		}
		
		post.setEntity(new UrlEncodedFormEntity(data,"UTF-8"));
		
		HttpResponse response = client.execute(post);
		int statusCode = response.getStatusLine().getStatusCode();
		String pageString = getResponseContent(response);
		System.out.println(pageString);
		if (statusCode != 302) {
			System.err.println(email + " regist xiami failed:" + statusCode);
			System.err.println(email + " detail:" + pageString);
			return null;
		}
		System.out.println(email + " regist xiami success");
		return setupReturn(email);
	}
	
	public List<String> loginXM() throws ClientProtocolException, IOException {
		if (this.online) {
			return setupReturn(this.id);
		}
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
		String pageString = getResponseContent(response);
		if (statusCode != 302) {
			System.err.println(this.id + " login xiami failed:" + statusCode);
			System.err.println(this.id + " detail:" + pageString);
			return null;
		}
		String cookie = "";
		for (Header header : response.getHeaders("Set-Cookie")) {
			cookie = header.getValue();
			if (cookie.contains(xiamiTokenKey)) {
				String authCookie = cookie.substring(
						cookie.indexOf(xiamiTokenKey + "=")
								+ (xiamiTokenKey + "=").length(),
						cookie.indexOf(";"));
				this.xiamiToken = authCookie;
				this.parsedXiamiToken = this.xiamiToken.replace("%2B", "+")
						.replace("%2F", "/");
			} else if (cookie.contains(memberAuthKey)) {
				String authCookie = cookie.substring(
						cookie.indexOf(memberAuthKey + "=")
								+ (memberAuthKey + "=").length(),
						cookie.indexOf(";"));
				this.rawMemberAuth = authCookie;
				this.parsedMemberAuth = this.rawMemberAuth.replace("%2B", "+")
						.replace("%2F", "/");
			}
		}
		this.online = true;
		return setupReturn(this.id);
	}
	
	private List<String> setupReturn(String... args) {
		if (args == null || args.length == 0) {
			return null;
		} 
		List<String> ret = new ArrayList<String>();
		for (String arg : args) {
			ret.add(arg);
		}
		return ret;
	}
	
	public List<String> logoutXM() throws ClientProtocolException, IOException {
		if (!this.online) {
			return setupReturn(this.id);
		}			
		HttpPost get = new HttpPost(logoutXMUrl);
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept", acceptHeader);
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Charset", acceptCharsetHeader);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);

		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		String pageString = getResponseContent(response);
		if (statusCode != 302) {
			System.err.println(this.id + " logout xiami failed:" + statusCode);
			System.out.println("detail:"+pageString);
			return null;
		}
		this.online = false;
		this.xiamiToken = "";
		return setupReturn(this.id);
	}

	private String getResponseContent(HttpResponse response) {
		StringBuffer page = new StringBuffer();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent(), "UTF-8"));
			String line = "";
			while ((line = br.readLine()) != null) {
				page.append(line);
			}
			br.close();
		} catch (IllegalStateException | IOException e) {
			e.printStackTrace();
		}
		return page.toString();
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
		this.parsedXiamiToken = this.xiamiToken.replace("%2B", "+").replace(
				"%2F", "/");
		response.getEntity().consumeContent();
		return true;
	}

	public List<String> setupUidNick() throws ClientProtocolException,
			IOException {
		if (null != this.uid && null != this.nick) {
			return setupReturn(this.uid,this.nick);
		}
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" setup uid failed: login xm failed");
				return null;
			}
		}
		HttpGet get = new HttpGet("http://www.xiami.com/web/profile");
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept", acceptHeader);
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Referer", "http://www.xiami.com/web");
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		HttpResponse response = client.execute(get);
		String pageString = getResponseContent(response);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.err.println(this.id + " setup uid failed : " + statusCode);
			System.out.println("detail:" + pageString);
			return null;
		}
		Matcher uidM = uidRe.matcher(pageString);
		if (uidM.find()) {
			this.uid = uidM.group(1);
		}
		Matcher nickM = nickRe.matcher(pageString);
		if (nickM.find()) {
			this.nick = nickM.group(1);
		}		
		return this.setupReturn(this.uid,this.nick);
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
		gbParams.put("code", this.parsedXiamiToken);
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
		chatParams.put("code", this.parsedXiamiToken);
		chatParams.put("room_id", this.room + "");
		chatParams.put("msg", this.chatMessage);
		(new Thread(new LoopTaskThread(socket, this.chatPeriod * 1000,
				"CommonMsg", chatParams, chatSignal))).start();
		return true;
	}
	private Pattern checkinUrlPattern = Pattern.compile("class=\"check_in\"\\s+href=\"(.*?)\"");
	public List<String> dailySignin() throws ClientProtocolException,
			IOException {
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" daily signin failed: login xm failed");
				return null;
			}
		}
		if (null == this.uid) {
			if (null == this.setupUidNick()) {
				System.err.println(this.id+" daily signin failed: setup uid failed");
				return null;
			}
		}
		String pageContent = this.visitPage("www.xiami.com/web");
		Matcher m = checkinUrlPattern.matcher(pageContent);
		String checkinUrl = "";
		if (m.find()) {
			checkinUrl = m.group(1);
		} else {
			throw new RuntimeException("daily signin failed:"+pageContent);
		}
		HttpPost get = new HttpPost("http://www.xiami.com"+checkinUrl);
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Referer", "http://www.xiami.com/web");
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		get.addHeader("Connection", connectionHeader);
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		String pageString = getResponseContent(response);
		if (statusCode != 302) {
			System.err.println(this.id + " signin daily failed:" + statusCode);
			System.err.println("detail:" + pageString);
			return setupReturn("fail");
		}
		System.out.println(this.id + " signin daily success");		
		System.out.println("detail:" + pageString);
		return setupReturn("success");
	}

	public List<String> replyComment(String cidStr, String comment)
			throws ClientProtocolException, IOException {
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" reply comment failed: login xm failed");
				return null;
			}
		}
		String[] cids = cidStr.split(":");
		String cid = cids[0];
		String type = cids[1];
		HttpPost post = new HttpPost("http://www.xiami.com/commentlist/re/id/"
				+ cid + "/type/4");
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		post.addHeader("Host", xmHostHeader);
		post.addHeader("User-Agent", userAgentHeader);
		post.addHeader("Accept", "*/*");
		post.addHeader("Accept-Language", acceptLanguageHeader);
		post.addHeader("Connection", connectionHeader);
		post.addHeader("Content-Type",
				"application/x-www-form-urlencoded; charset=UTF-8");
		post.addHeader("X-Requested-With", "XMLHttpRequest");
		post.addHeader("Accept-Charset", acceptCharsetHeader);
		post.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);

		data.add(new BasicNameValuePair("content", comment));
		data.add(new BasicNameValuePair("relids", ""));
		data.add(new BasicNameValuePair("type", "4"));
		data.add(new BasicNameValuePair(xiamiTokenKey, xiamiToken));
		if (type.equals("reply")) {
			data.add(new BasicNameValuePair("act", "quote"));			
		}
		try {
			post.setEntity(new UrlEncodedFormEntity(data, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		HttpResponse response = client.execute(post);
		int statusCode = response.getStatusLine().getStatusCode();
		String pageString = getResponseContent(response);
		if (statusCode != 200) {
			System.err.println(this.id + " reply " + comment + " to " + cid
					+ " failed:" + statusCode);
			System.err.println("detail:" + pageString);
			return setupReturn("fail");
		}

		System.err.println(this.id + " reply " + comment + " to " + cid
				+ " success");
		Matcher m = replyIdPattern.matcher(pageString);
		if (m.find()) {
			System.out.println("detail:" + m.group(1));
			return setupReturn(m.group(1)+":"+"reply");
		} else {
			System.out.println("detail:get reply id error:" + pageString);
			return setupReturn("fail");
		}
	}

	public List<String> judgeComment(String cidStr, String rate)
			throws ClientProtocolException, IOException {
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" judge comment failed: login xm failed");
				return null;
			}
		}
		if (null == this.uid) {
			if (null == this.setupUidNick()) {
				System.err.println(this.id+" judge comment failed: setup uid failed");
				return null;
			}
		}
		List<String> ret = new ArrayList<String>();
		int realId = Integer.parseInt(cidStr.substring(0,cidStr.indexOf(":")));
		rate = Integer.parseInt(rate) > 0 ? "1" : "2";
		HttpGet get = new HttpGet(
				"http://www.xiami.com/commentlist/ageree/?id=" + realId
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
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.out.println(this.id + " judge " + rate + " to " + cidStr
					+ " failed");
			ret.add("fail");
			return ret;
		}
		String pageString = getResponseContent(response);
		DEBUG(pageString);
		System.out.println(this.id + " judge " + rate + " to " + cidStr
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

	public List<String> postCommentToArtist(String artistId, String comment)
			throws ClientProtocolException, IOException {
		return postCommentToObject(3, artistId, comment);
	}
	
	public List<String> postCommentToObject(int objectType, String objectId, String comment)
			throws ClientProtocolException, IOException {
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" post comment to song failed: login xm failed");
				return null;
			}
		}
		HttpGet get = new HttpGet(
				"http://www.xiami.com/commentlist/add?type="+objectType+"&oid=" + objectId
						+ "&content=" + URLEncoder.encode(comment, "UTF-8")
						+ "&relids=&mode=ajax&_xiamitoken=" + this.xiamiToken);
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept",
				"application/json, text/javascript, */*; q=0.01");
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("X-Requested-With", "XMLHttpRequest");
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Referer", "http://" + xmHostHeader + "/"+objectTypeName.get(objectType)+"/" + objectId);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		HttpResponse response = client.execute(get);

		String pageString = getResponseContent(response);
		DEBUG(pageString);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.out.println(this.id + " post " + comment + " to " + objectId
					+ " failed : " + statusCode);			
			return setupReturn("fail");
		}
		try {
			JSONObject returnJson = new JSONObject(pageString);
			if (returnJson.get("status").equals("failed")) {
				System.err.println(this.id + " post " + comment + " to "
						+ objectId + " failed");
				System.err.println("detail:" + returnJson.get("msg"));
				return setupReturn("fail");
			} else if (returnJson.get("status").equals("ok")) {
				System.out.println(this.id + " post " + comment + " to "
						+ objectId + " success");
				Matcher m = commentIdPattern.matcher(returnJson
						.getString("output"));
				if (m.find()) {
					System.out.println("detail:" + m.group(1));
					return setupReturn(m.group(1)+":"+"post");
				} else {
					System.err.println("detail:get comment id failed");
					return setupReturn("fail");
				}
			}
		} catch (JSONException e) {
			System.err.println(this.id + " post " + comment + " to " + objectId
					+ " failed");
			System.err.println("detail:" + pageString);
			e.printStackTrace();
		}
		return null;
	}
	
	
	public List<String> postCommentToAlbum(String albumId, String comment)
			throws ClientProtocolException, IOException {
		return postCommentToObject(1, albumId, comment);
	}
	
	
	public List<String> postCommentToSong(String songId, String comment)
			throws ClientProtocolException, IOException {
		return postCommentToObject(4, songId, comment);
	}
	
	public List<String> shareCollect(String cid, String shareComment) throws ClientProtocolException, IOException {
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" post comment to song failed: login xm failed");
				return null;
			}
		}

		HttpPost post = new HttpPost("http://www.xiami.com/recommend/post");
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		post.addHeader("Host", xmHostHeader);
		post.addHeader("User-Agent", userAgentHeader);
		post.addHeader("Accept", "*/*");
		post.addHeader("Accept-Language", acceptLanguageHeader);
		post.addHeader("Connection", connectionHeader);
		post.addHeader("Content-Type",
				"application/x-www-form-urlencoded; charset=UTF-8");
		post.addHeader("X-Requested-With", "XMLHttpRequest");
		post.addHeader("Referer", "http://www.xiami.com/song/showcollect/id/"+cid);		
		post.addHeader("Accept-Charset", acceptCharsetHeader);
		post.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		
		data.add(new BasicNameValuePair("share_status", "0"));
		data.add(new BasicNameValuePair("sid", ""));
		data.add(new BasicNameValuePair("type", "35"));
		data.add(new BasicNameValuePair("object_id", cid));
		data.add(new BasicNameValuePair(xiamiTokenKey, xiamiToken));		
		if (null == shareComment) {
			shareComment = "";
		}
		data.add(new BasicNameValuePair("message", shareComment));
		data.add(new BasicNameValuePair("submit", "分 享"));
		
		try {
			post.setEntity(new UrlEncodedFormEntity(data, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		HttpResponse response = client.execute(post);
		int statusCode = response.getStatusLine().getStatusCode();
		String pageString = getResponseContent(response);
		if (statusCode != 200) {
			System.err.println(this.id + " share collect " + cid + " failed:" + statusCode);
			System.err.println("detail:" + pageString);
			return setupReturn("fail");
		}

		System.out.println(this.id + " share collect " + cid + " success");
		return setupReturn("success");
	}
	
	public List<String> unfollow(String uid) throws ClientProtocolException,
			IOException {
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" unfollow failed: login xm failed");
				return null;
			}
		}
		HttpGet get = new HttpGet("http://www.xiami.com/member/attention/uid/"
				+ uid + "/type/2?" + xiamiTokenKey + "=" + xiamiToken);
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Referer", "http://www.xiami.com/u/" + uid);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		HttpResponse response = client.execute(get);
		String pageString = getResponseContent(response);
		DEBUG(pageString);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.err.println(this.id + " unfollow " + uid + " failed : "
					+ statusCode);
			return setupReturn("fail");
		}
		System.out.println(this.id + " unfollow " + uid + " success");
		return setupReturn("success");
	}

	public List<String> follow(String uid) throws ClientProtocolException,
			IOException {
		if (!this.online) {
			if (null == this.loginXM()) {
				System.err.println(this.id+" follow failed: login xm failed");
				return null;
			}
		}
		HttpGet get = new HttpGet(
				"http://www.xiami.com/member/attention/from/ajax/type/1/uid/"
						+ uid + "?" + xiamiTokenKey + "=" + xiamiToken);
		get.addHeader("Host", xmHostHeader);
		get.addHeader("User-Agent", userAgentHeader);
		get.addHeader("Accept", "text/html, */*; q=0.01");
		get.addHeader("Accept-Language", acceptLanguageHeader);
		get.addHeader("X-Requested-With", "XMLHttpRequest");
		get.addHeader("Connection", connectionHeader);
		get.addHeader("Accept-Encoding", acceptEncodingHeader);
		get.addHeader("Referer", "http://www.xiami.com/u/" + uid);
		get.addHeader("Cookie", xiamiTokenKey + "=" + this.xiamiToken);
		HttpResponse response = client.execute(get);
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode != 200) {
			System.out.println(this.id + " follow " + uid + " failed : "
					+ statusCode);
			return setupReturn("fail");
		}
		String pageString = getResponseContent(response);
		DEBUG(pageString);
		if (pageString.indexOf("你已关注") != -1) {
			System.out.println(this.id + " follow " + uid + " success");
			return setupReturn(this.id);
		} else {
			System.out.println(this.id + " follow " + uid + " failed");
			System.out.println("detail:" + pageString);
			return setupReturn("fail");
		}
	}

	@Override
	public void run() {
		try {
			if (null != this.setupUidNick()) {
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void stopConnect() {
		if (this.socket != null) {
			this.socket.disconnect();
		}
	}
}
