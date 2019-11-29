package testBoardTool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;



public class TcpClient {
	private Thread thread;
	private String cp200nIPAddress = "192.168.3.132";
	private int portNumber = 1234;
	private Socket socket;
	private OutputStream outputstream;
	private InputStream inputstream;
	public boolean breakFlag = true;
	public interface Cp200nListener{
		void gotRecv(byte[] recvbuf);
	};
	//private HopperListener hopperListener;
	List<Cp200nListener> maplist = new ArrayList<Cp200nListener>();
	public void setCp200nListener(Cp200nListener cp200nListener) {
		maplist.add(cp200nListener);
	}
	
	/**
	 * ���췽�����������ӣ���׼����������
	 */
	public TcpClient() {
		try {
			socket = new Socket(cp200nIPAddress, portNumber);// �����˽�������
			//socket.shutdownOutput();//ͨ��shutdownOutput���ٷ������Ѿ����������ݣ�����ֻ�ܽ�������
			TcpRecvData tcpRecvData = new TcpRecvData();
			thread = new Thread(tcpRecvData);
			thread.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * ���췽�����������ӣ���׼����������
	 * @param	String 		cp200nipaddress		���ư��IP��ַ
	 */
	public TcpClient(String cp200nipaddress) {
		this.cp200nIPAddress = cp200nipaddress;
		try {
			socket = new Socket(cp200nIPAddress, portNumber);// �����˽�������
			byte[] resetdat = new byte [3];
			resetdat[0] = 7;resetdat[1] = 7;resetdat[2] = 7;
			TcpSendData(resetdat);
			//socket.shutdownOutput();//ͨ��shutdownOutput���ٷ������Ѿ����������ݣ�����ֻ�ܽ�������
			if(socket != null) {
				TcpRecvData tcpRecvData = new TcpRecvData();
				thread = new Thread(tcpRecvData);
				thread.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * �����������ر����ӣ��ͷ���Դ
	 */
	protected void finalize(){
		closeAllConnect();
		System.out.println("close " + cp200nIPAddress + " connect!"); 
	}
	
	/**
	 * TCP��������
	 * @param	byte[]	sendbuf		������������
	 */
	public void TcpSendData(byte[] sendbuf) {
		try {
			outputstream = socket.getOutputStream();// �������Ӻ��������
			socket.getOutputStream().write(sendbuf);
			//socket.shutdownOutput();//ͨ��shutdownOutput���ٷ������Ѿ����������ݣ�����ֻ�ܽ�������
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*
	 * �����������л�ȡ�����ݣ������������Ĵ����¼�
	 */
	private void proInputStream(byte[] recvData, int length) {
		for ( Cp200nListener b : maplist) {
			if(b != null) {
				byte[] tmpData = new byte [length];
				for(int i = 0; i < tmpData.length; i++) {
					tmpData[i] = recvData[i];
				}
				b.gotRecv(tmpData);
			}
		}
	}
	
	private class TcpRecvData implements Runnable {
		public TcpRecvData() {
			try {
				inputstream = socket.getInputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void run() {
			try {
				inputstream = socket.getInputStream();
				byte[] bytes = new byte[1024];
			    int len;
			    ByteArrayOutputStream bos = new ByteArrayOutputStream();
			    //StringBuilder sb = new StringBuilder();
			    //while(true) {
			    	//do {
				      	//ע��ָ�������ʽ�����ͷ��ͽ��շ�һ��Ҫͳһ������ʹ��UTF-8
			    		//len = inputstream.read(bytes);
	                	//bos.write(bytes, 0, len);
	                	//proInputStream(bytes, len);
				    //} while(inputstream.available() != 0);
			    	while(breakFlag) {
			    		if(inputstream.available() != 0) {
			    			len = inputstream.read(bytes);
			    			proInputStream(bytes, len);
			    		}
			    	}
			    	/*while ((len = inputstream.read(bytes)) != -1) {
				    //while ((!thread.isInterrupted() || inputstream.available() != 0) && (len = inputstream.read(bytes)) != -1) {
				      //ע��ָ�������ʽ�����ͷ��ͽ��շ�һ��Ҫͳһ������ʹ��UTF-8
				      //sb.append(new String(bytes, 0, len,"UTF-8"));
			    		proInputStream(bytes, len);
				    }*/
				    System.out.println(cp200nIPAddress + " got message: " + bytes);
				    if(!breakFlag) {
				    	socket.close();//�ͷ�����
				    }
			    //}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * �ر���������
	 */
	public void closeAllConnect() {
		try {
			/*if(inputstream != null) {
				inputstream.close();//�ر�������
			}
			if(outputstream != null) {
				outputstream.close();//�ر������
			}*/
			
			if(thread != null) {
				//ֹͣ�̵߳�����
				breakFlag = false;
				thread.interrupt();
				thread = null;
			}
			/*if(socket != null) {
				socket.close();//�ͷ�����
			}*/
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
