package org.haibara.autoxm;

import io.socket.SocketIO;

import java.util.Map;

public class XMTaskThread implements Runnable {
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
	public void stopWork () {
		this.signal = false;
	}
}