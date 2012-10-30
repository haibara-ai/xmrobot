package org.haibara.autoxm;

import info.clearthought.layout.TableLayout;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import org.jdesktop.application.SingleFrameApplication;

/**
 * This code was edited or generated using CloudGarden's Jigloo
 * SWT/Swing GUI Builder, which is free for non-commercial
 * use. If Jigloo is being used commercially (ie, by a corporation,
 * company or business for any purpose whatever) then you
 * should purchase a license for each developer using Jigloo.
 * Please visit www.cloudgarden.com for details.
 * Use of Jigloo implies acceptance of these licensing terms.
 * A COMMERCIAL LICENSE HAS NOT BEEN PURCHASED FOR
 * THIS MACHINE, SO JIGLOO OR THIS CODE CANNOT BE USED
 * LEGALLY FOR ANY CORPORATE OR COMMERCIAL PURPOSE.
 */
/**
 * 
 */
public class MainFrame extends SingleFrameApplication {
	private JPanel topPanel;
	private JLabel chatLabel;
	private JButton chatButton;
	private JTextArea jTextArea1;
	private JTextField chatField;

	@Override
	protected void startup() {
		FlowLayout mainFrameLayout = new FlowLayout();
		getMainFrame().getContentPane().setLayout(mainFrameLayout);
		{
			topPanel = new JPanel();
			TableLayout topPanelLayout = new TableLayout(new double[][] {{70.0, TableLayout.FILL, 142.0, TableLayout.FILL}, {14.0, 35.0, 11.0, TableLayout.FILL}});
			topPanelLayout.setHGap(5);
			topPanelLayout.setVGap(5);
			topPanel.setLayout(topPanelLayout);
			getMainFrame().getContentPane().add(topPanel);
			topPanel.setPreferredSize(new java.awt.Dimension(364, 331));
			{
				chatLabel = new JLabel();
				topPanel.add(chatLabel, "0,1,c,c");
				chatLabel.setName("chatLabel");
			}
			{
				chatField = new JTextField();
				topPanel.add(chatField, "1, 1, 2, 1");
				chatField.setName("chatField");
			}
			{
				chatButton = new JButton();
				topPanel.add(chatButton, "3,1,c,c");
				chatButton.setName("chatButton");
				chatButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent evt) {
						chatButtonActionPerformed(evt);
					}
				});
			}
			{
				jScrollPane1 = new JScrollPane();
				topPanel.add(jScrollPane1, "0, 3, 3, 3");
				{
					jTextArea1 = new JTextArea();
					jScrollPane1.setViewportView(jTextArea1);
					jTextArea1.setName("jTextArea1");
					jTextArea1.setLocale(new java.util.Locale("zh"));
				}
			}
		}
		{
			getMainFrame().setSize(380, 372);
		}
		show(topPanel);
		this.myStartup();
	}

	private void myStartup() {
		ps = new PrintStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {

			}

			public void write(byte data[], int off, int len) throws IOException {
				final String message = new String(data, off, len);
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						sb.append(message);
						jTextArea1.setText(sb.toString());
						jTextArea1.setCaretPosition(jTextArea1.getText().length());
					}
				});
			}
		});
		System.setOut(ps);
		System.setErr(ps);
		jTextArea1.append("Loading...");
		String rootPath = MainFrame.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		rootPath = rootPath.substring(rootPath.indexOf("/")+1,rootPath.lastIndexOf("/"));
		XMDriver.root = rootPath;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				driver = new XMDriver();
				try {
					driver.start();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});	
	}

	private StringBuffer sb = new StringBuffer();
	private PrintStream ps = null;
	private XMDriver driver = null;

	public static void main(String[] args) {
		launch(MainFrame.class, args);
	}

	private boolean chatState = false;
	private JScrollPane jScrollPane1;

	private void chatButtonActionPerformed(ActionEvent evt) {
		if (chatState == true) {
			if (this.driver != null) {
				this.driver.stopAudienceChat();
			}
			chatState = false;
			this.chatButton.setText("start");
		} else {
			if (!this.chatField.getText().trim().isEmpty()) {
				if (this.driver != null) {
					this.driver.setAudienceChat(this.chatField.getText());
				}
				chatState = true;
				this.chatButton.setText("stop");
			} else {
				
			}
		}
	}

}
