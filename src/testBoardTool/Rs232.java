package testBoardTool;

import java.util.ArrayList;
import java.util.List;

import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import serial.test.SendDataToSerialPortFailure;
import serial.test.SerialPortOutputStreamCloseFailure;
import serial.test.NoSuchPort;
import serial.test.NotASerialPort;
import serial.test.PortInUse;
import serial.test.SerialPortParameterFailure;
import serial.test.TooManyListeners;
import serial.test.ReadDataFromSerialPortFailure;
import serial.test.SerialPortInputStreamCloseFailure;
import serial.test.SerialTool;

public class Rs232 {
	private SerialPort serialPort = null;	//���洮�ڶ���
	private boolean comOpenedFlag = false;	//�����Ѵ򿪱�־λ
	private String comName;//��ȡ��������
	private int bps = 115200;
	private int parity = SerialPort.PARITY_NONE;
	
	public interface Cp168Listener{
		void gotRecv(byte[] recvbuf);
	};
	//private HopperListener hopperListener;
	List<Cp168Listener> maplist = new ArrayList<Cp168Listener>();
	public void setCp168Listener(Cp168Listener cp168Listener) {
		maplist.add(cp168Listener);
	}
	
	public Rs232(String comNum) {
		comName = comNum;
		//parityStr = SerialPort.PARITY_ODD;
		//parityStr = SerialPort.PARITY_EVEN;
		//parityStr = SerialPort.PARITY_NONE;
		openCloseComPort(true);
	}
	
	/**
	 * ���ڷ�������
	 * @param byte[]	sendbuf ���͵���������
	 */
	public void ComSendData(byte[] sendbuf) {
		try{
			SerialTool.sendToPort(serialPort, sendbuf);
		} catch(SendDataToSerialPortFailure | SerialPortOutputStreamCloseFailure e4) {
			System.out.println("[ERROR]Cp168-SendDataToSerialPortFailure, SerialPortOutputStreamCloseFailure");
		}
	}

	/**
	 * �򿪻�ر�Hopper�Ĵ���
	 * @param boolean	open_true ���Ϊtrue��򿪴��ڣ����Ϊfalse��رմ���
	 */
	public void openCloseComPort(boolean open_true) {
		if(open_true) {
			if(comOpenedFlag) {
				return;
			}
		} else {
			if(!comOpenedFlag) {
				return;
			}
		}
		if(open_true) {
			if (comName == null || comName.equals("")) {//��鴮�������Ƿ��ȡ��ȷ
				System.out.println("[ERROR]Hopper-���ں���Ч");			
			} else {
				try {
					serialPort = SerialTool.openPort(comName, bps, parity, SerialPort.DATABITS_8, SerialPort.STOPBITS_1);//��ȡָ���˿����������ʵĴ��ڶ���
					SerialTool.addListener(serialPort, new SerialListener());//�ڸô��ڶ�������Ӽ�����
					System.out.println("[ERROR]Cp168-���ڴ򿪳ɹ�����ʼ����");
					comOpenedFlag = true;
				} catch (SerialPortParameterFailure | NotASerialPort | NoSuchPort | PortInUse | TooManyListeners e1) {
					System.out.println("[ERROR]Cp168-���ڴ򿪳���");
					comOpenedFlag = false;
					e1.printStackTrace();
				}
			}
		} else {
			SerialTool.closePort(serialPort);
			comOpenedFlag = false;
		}
	}
	
	/*
	 * �����������л�ȡ�����ݣ������������Ĵ����¼�
	 */
	private void proReceive(byte[] recvbuf) {
		for ( Cp168Listener b : maplist) {
			if(b != null) {
				b.gotRecv(recvbuf);
			}
		}
	}
	
	/**
	 * ���ڲ�����ʽ����һ�����ڼ�����
	 * @author xuxingxing
	 *
	 */
	private class SerialListener implements SerialPortEventListener {
	    public void serialEvent(SerialPortEvent serialPortEvent) {//�����ص��Ĵ����¼�
	        switch (serialPortEvent.getEventType()) {
	            case SerialPortEvent.BI: // 10 ͨѶ�ж�
	            	System.out.println("[ERROR]CP168-�봮���豸ͨѶ�ж�");
	            	break;
	            case SerialPortEvent.OE: // 7 ��λ�����������
	            case SerialPortEvent.FE: // 9 ֡����
	            case SerialPortEvent.PE: // 8 ��żУ�����
	            case SerialPortEvent.CD: // 6 �ز����
	            case SerialPortEvent.CTS: // 3 �������������
	            case SerialPortEvent.DSR: // 4 ����������׼������
	            case SerialPortEvent.RI: // 5 ����ָʾ
	            case SerialPortEvent.OUTPUT_BUFFER_EMPTY: // 2 ��������������
	            	break;
	            case SerialPortEvent.DATA_AVAILABLE: // 1 ���ڴ��ڿ�������
	            	//System.out.println("found data");
					byte[] data = null;
					try {
						if (serialPort == null) {
							System.out.println("[ERROR]CP168-���ڶ���Ϊ�գ�����ʧ�ܣ�");
						}
						else {
							data = SerialTool.readFromPort(serialPort);	//��ȡ���ݣ������ֽ�����
							//�Զ����������
							if (data == null || data.length < 1) {	//��������Ƿ��ȡ��ȷ
								System.out.println("[ERROR]CP168-��ȡ���ݹ�����δ��ȡ����Ч���ݣ������豸�����");
								//System.exit(0);//------------------------------
							}
							else {
								proReceive(data);
								//receiveTextArea.append(getReceiveData(data));
								//receiveCountField.setText(Integer.toString(Integer.parseInt(receiveCountField.getText()) + data.length));
							}
						}						
					} catch (ReadDataFromSerialPortFailure | SerialPortInputStreamCloseFailure e) {
						System.out.println("[ERROR]CP168-ReadDataFromSerialPortFailure, SerialPortInputStreamCloseFailure");
						//System.exit(0);	//������ȡ����ʱ��ʾ������Ϣ���˳�ϵͳ//------------------------------
					}	
					break;
	        }
	    }
	}
}

