package org.haibara.autoxm;

import io.socket.IOAcknowledge;
import io.socket.SocketIOException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.haibara.autoxm.LoopTaskThread;
import org.haibara.io.DataHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class XMDJ extends XMAudience{
	
	public XMDJ(String id, String pw, Map<String, String> taskMap) {
		super(id, pw, taskMap);
		this.maxLoopVal = Integer.parseInt(GetProperty("max_loop_val"));
		this.autoNextPeriod = Integer.parseInt(GetProperty("auto_next_period"));
		this.startLoopVal = Integer.parseInt(GetProperty("start_loop_val"));
		if (this.autoNextPeriod > 5) {
			this.autoPlaySignal = true;
		}
	}
	
	private int maxLoopVal = 0;
	private int userCount = -1;
	private int autoNextPeriod = 0;
	private int loopVal = -1;
	private int startLoopVal = -1;
	private int playListCount = -1;
	
	private boolean isDJ = false;
	private boolean autoPlaySignal = false;
	
	private LoopTaskThread playThread = null;
	private XMDJ cascadeDJ = null;	
	
	private XMDriver driver = null;
	
	public void setXMDriver(XMDriver driver) {
		this.driver = driver;
	}
	
	public XMAudience castToAudience() {
		return null;
	}
	
	public void addCascadeDJ(XMDJ dj) {
		this.cascadeDJ = dj;
	}
	
	public void MessageHandler(JSONObject json, IOAcknowledge ack) {
	}
	public void MessageHandler(String data, IOAcknowledge ack) {
		super.MessageHandler(data, ack);
	}
	public void ErrorHandler(SocketIOException socketIOException) {
		super.ErrorHandler(socketIOException);
	}
	
	public void DisconnectHandler() {
		atRoom = false;
		logLoopVal(loopVal);
		System.out.println(time.format(new Date())+", "+this.nick+" endLoop:"+loopVal);
		if (cascadeDJ != null) {
			this.stopWork();
			new Thread(cascadeDJ).start();
		} else if (cascadeDJ == null) {
			if (this.driver != null) {
				this.driver.exit();
			}
		}
	}
	public void ConnectHandler() {
		
	}
	public void EventHandler(String event, IOAcknowledge ack, Object... args) {
		super.EventHandler(event, ack, args);
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
			if (atRoom && !isDJ) {
				setDJ();
			}
		}
		if ("SetDJ".equals(event)) {
			try {
				JSONArray json = new JSONArray(args);
				JSONObject job = json.getJSONObject(0);
				if (job.getString("user_id").equals(uid)) {
					isDJ = true;
					if (autoPlaySignal) {
						startAutoPlayTask();
					} else {
						manualPlayNext();
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		if ("LeaveDJ".equals(event)) {
			isDJ = false;
			this.stopConnect();
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
	
	public void setPlayPeriod(int period) {
		this.autoNextPeriod = period;
		if (this.playThread != null) {
			this.playThread.setPeriod(this.autoNextPeriod);
		}
	}
	
	private void logLoopVal(int loopVal) {
		if (loopVal != -1) {
			DataHandler.appendFile(time.format(new Date()) + "=" + loopVal
					+ "\n", XMDriver.root+XMDriver.logDir+"." + uid);
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
	
	public boolean manualPlayNext() {
		System.out.println(time.format(new Date())+", "+this.nick+" startLoop:"+loopVal);
		Map<String, String> jsonMap = new HashMap<String, String>();
		jsonMap.put("user_id", this.uid);
		jsonMap.put("room_id", this.room + "");
		jsonMap.put("code", this.parsedMemberAuth);
		DEBUG("userCount:" + userCount + " startLoop:" + startLoopVal
				+ " curLoop:" + loopVal);
		this.socket.emit("PlayNext", jsonMap);
		return true;
	}
	
	public void stopAutoPlay() {
		if (this.autoPlaySignal == true) {
			this.autoPlaySignal = false;
			if (this.playThread != null) {
				this.playThread.setSignal(this.autoPlaySignal);
			}
		}
	}
	
	public boolean startAutoPlayTask() {
		System.out.println(time.format(new Date())+", "+this.nick+" startLoop:"+loopVal);
		Map<String, String> jsonMap = new HashMap<String, String>();
		jsonMap.put("user_id", this.uid);
		jsonMap.put("room_id", this.room + "");
		jsonMap.put("code", this.parsedMemberAuth);		
		playThread = new LoopTaskThread(socket,
				this.autoNextPeriod * 1000, "PlayNext", jsonMap, autoPlaySignal);
		new Thread(playThread).start();
		return true;
	}
	
	public void stopWork() {
		if (this.playThread != null) {
			this.playThread.stopWork();
		}
	}
}
