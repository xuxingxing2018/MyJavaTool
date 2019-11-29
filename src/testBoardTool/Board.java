package testBoardTool;


import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import testBoardTool.Rs232.Cp168Listener;
import testBoardTool.TcpClient.Cp200nListener;



public class Board {
	private static final int BUF_LENGTH = 65536;
	private byte [] RecvDataBuf = new byte [BUF_LENGTH];//�������ݻ�����
	private byte [] SendDataBuf = new byte [BUF_LENGTH];//�������ݻ�����
	private int RecvAddIndex;						//��������ָ��
	private int RecvDoneIndex;						//���ݴ���ָ��
	private int SendAddIndex;						//��������ָ��
	private byte RecvFc1;							//����Э��Ĺ�����1
	private byte RecvFc2;							//����Э��Ĺ�����2
	private byte RecvCrc;							//����Э���У����
	private byte SendCrc;							//����Э���У����
	private final byte RECV_HEAD = 3;				//����Э���Э��ͷ
	private final byte RECV_END = 4;				//����Э���Э��β
	private final byte RECV_ETX = 14;				//����Э������ݽ�����
	
	private final byte SEND_HEAD = 1;				//����Э���Э��ͷ
	private final byte SEND_END = 2;				//����Э���Э��β
	private final byte SEND_ETX = 14;				//����Э������ݽ�����
	private TcpClient tcpClient;
	private Rs232 rs232;
	private boolean isTcpFlag;						//
	private JScrollPane scrollPane;					//��message ��Ӧ�Ĺ�����
	
	private String RecvDataString;					//������ɺ���ַ������
	private JTextArea RecvMessageArea;
	private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5', 
            '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	
	
	
	public Board(boolean isTcpFlag, String ipAddress_ComNum, 
			JTextArea recvMessageArea, JScrollPane scrollPane) {//���캯��
		RecvAddIndex = 0;							//���ָ��
		RecvDoneIndex = 0;							//���ָ��
		SendAddIndex = 0;							//���ָ��
		this.isTcpFlag = isTcpFlag;
		RecvMessageArea = recvMessageArea;
		this.scrollPane = scrollPane;
		if(isTcpFlag) {//ͨ��TCP��ʽ�����豸
			tcpClient = new TcpClient(ipAddress_ComNum);
			tcpClient.setCp200nListener(new Cp200nListener() {//����TCP���ղ�����
				public void gotRecv(byte[] recvbuf) {
					Recv(recvbuf);//������յ�����
				}
			});
		} else {//ͨ��COM��ʽ�����豸
			rs232 = new Rs232(ipAddress_ComNum);
			rs232.setCp168Listener(new Cp168Listener() {//�������ڽ��ղ�����
				public void gotRecv(byte[] recvbuf) {
					Recv(recvbuf);//������յ�����
				}
			});
		}
	}
	
	/**
	 * �ر�����
	 */
	public void disconnect() {
		if(isTcpFlag) {//ͨ��TCP��ʽ�����豸
			tcpClient.closeAllConnect();
		} else {
			rs232.openCloseComPort(false);
		}
	}
	
	/**
	 * У��ʱ�ӣ�����ʾ����ʾ��������ʾ
	 */
	public void setNowTime() {
		getSendDataHead((byte)0x41, (byte)0x30);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");//�������ڸ�ʽ
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
		if (w < 0) w = 0;
		String[] weekDays = { "00", "01", "02", "03", "04", "05", "06" };
		String nowTime = df.format(new Date()) + weekDays[w];
        //System.out.println(nowTime);
		byte[] myByte = nowTime.getBytes();
		for(int i = 0; i < myByte.length; i++) {
			getSendData1Byte(myByte[i]);
		}
		getSendDataEnd();
		send();
	}

	/**
	 * ���ÿ��ư�ģʽ������
	 * @param byte mode_Data[0] ���ư�ģʽ��=0x30Ϊ��ͨģʽ��=0x31Ϊ��һ�������ģʽ��=0x32Ϊģ��621ģʽ��=0x33Ϊ�ѻ��շ�ģʽ��=0x3fΪ������
	 * @param byte mode_Data[1] ��������ͣ�=0x30-7098�������=0x31-1���������=0x32-7182�������=0x33-7183�������
	 * 		=0x34-4���������=0x35-5���������=0x36-6���������=0x37-7���������=0x38-8���������=0x39-9���������=0x3fΪ������
	 * @param byte mode_Data[2] ɨ�跽ʽ��=0x30Ϊ16ɨ��ʽ0��=0x31Ϊ16ɨ��ʽ1(δʵ��)��=0x32Ϊ8ɨ��ʽ0��=0x33Ϊ8ɨ��ʽ1(δʵ��)��
	 * 		=0x34Ϊ4ɨ��ʽ0��=0x35Ϊ4ɨ��ʽ1(δʵ��)��=0x3fΪ������
	 * @param byte mode_Data[3] ��Ļ�߶ȣ�=0x30Ϊһ�к��֣�=0x31Ϊ���к��֣�=0x32Ϊ���к��֣�=0x33Ϊ���к��֣�=0x3fΪ������
	 * @param byte mode_Data[4] ��Ļ��ȣ�=0x30Ϊ4���ֿ�ȣ�=0x31Ϊ5���ֿ��(δʵ��)��=0x32Ϊ8���ֿ�ȣ�=0x3fΪ������
	 * @param byte mode_Data[5] OE���ԣ�=0x30Ϊ�����ԣ�=0x31Ϊ�����ԣ�=0x3fΪ������
	 * @param byte mode_Data[6] ���ݼ��ԣ�=0x30Ϊ�����ԣ�=0x31Ϊ�����ԣ�=0x3fΪ������
	 * @param byte mode_Data[7] ɫ�����ͣ�=0x30Ϊ��ɫ��=0x31Ϊ˫��ɫ��=0x32Ϊȫ��(δʵ��)��=0x3fΪ������
	 */
	public void setBoardMode(byte [] mode_Data) {
		if(mode_Data.length != 8) {return;}
		getSendDataHead((byte)0x41, (byte)0x31);
		for(int i = 0; i < mode_Data.length; i++) {
			getSendData1Byte(mode_Data[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ���ư�ģʽ������
	 */
	public void readBoardMode() {
		getSendDataHead((byte)0x41, (byte)0x32);
		getSendDataEnd();
		send();
	}
	
	/**
	 * �����ֿ��ļ���������ļ���СΪ256KByte���������ֳ�4096�Σ�ÿ��64Byte��ÿ�ζ���˳�������š����Ϊ0x0000-0x0FFF������Ӧ������ִ��4096�Σ����ܴ��������ֿ��ļ���
	 * @param int	doc_index �ļ�����ָ�룬ȡֵ��Χ��0-4095
	 * @param byte[] file_64byte_data �ļ����ݣ���С��64�ֽ�
	 */
	public void downloadFontLib(int doc_index, byte[] file_64byte_data) {
		if(file_64byte_data.length != 64) {return;}
		if((doc_index >= 4096) || (doc_index < 0)) {return;}
		getSendDataHead((byte)0x41, (byte)0x33);
		byte dat = (byte)(doc_index >>> 8);
		getSendDataUnboxing(dat);
		dat = (byte)(doc_index);
		getSendDataUnboxing(dat);
		for(int i = 0; i < file_64byte_data.length; i++) {
			getSendDataUnboxing(file_64byte_data[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ������������
	 * @param boolean param_flag[0] �����������ƺ���,=true=0x30���������ƣ�=false=0x31��������
	 * @param boolean param_flag[1] 3�ż̵����Զ����ƺ��̵�,=true=0x30�Զ����ƺ��̵ƣ���Ч���̵ƣ���Ч����ƣ�=false=0x31�̵������������
	 * @param boolean param_flag[2] ��λ�ӿ�͸��ת����λ������, =true=0x30��Ҫת����=false=0x31��ת��
	 * @param boolean param_flag[3] ����AB�ӿڶ�������,=true=0x30���֣�=false=0x31������
	 * @param boolean param_flag[4] ������־,=true=0x30Ϊ������=false=0x31Ϊ����
	 * @param boolean param_flag[5] �ߵ���־,=true=0x30Ϊ�����ߵ���=false=0x31Ϊ���ߵ�
	 * @param boolean param_flag[6] ɨ��ǹ���ڽӿ�����־,=true=0x30�򴮿ڽ�ɨ��ǹ��=false=0x31�򴮿ڽӿ���
	 */
	public void setOtherParam(boolean [] param_flag) {
		if(param_flag.length != 7) {return;}
		getSendDataHead((byte)0x41, (byte)0x34);
		for(int i = 0; i < param_flag.length; i++) {
			if(param_flag[i]) {
				getSendData1Byte((byte)0x30);
			} else {
				getSendData1Byte((byte)0x31);
			}
			if((i == 1) || (i == 3) || (i == 5)) {
				getSendData1Byte((byte)0x40);
			}
		}
		getSendData1Byte((byte)0x41);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ��������
	 */
	public void readOtherParam() {
		getSendDataHead((byte)0x41, (byte)0x37);
		getSendDataEnd();
		send();
	}
	
	/**
	 * �����Զ�����ʱ�䣬����ʾ����ʾ��������ʾ
	 * @param	String lightOnTime �Զ�����ʱ�䣬ȡֵ��Χ0000-2359������0730������07:30
	 * @param	String lightOffTime �Զ��ص�ʱ�䣬ȡֵ��Χ0000-2359������1945������19:45
	 */
	public void setLightTime(String lightOnTime, String lightOffTime) {
		if((lightOnTime.length() != 4) || (lightOffTime.length() != 4)) return;
		getSendDataHead((byte)0x41, (byte)0x35);
		try {
			byte[] myByte;
			myByte = lightOnTime.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
			myByte = lightOffTime.getBytes("GB2312");
			i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ������Ƶ�ļ�����ע����Դ�ļ�Ϊbin�ļ�������ļ��а���2�����ֵ����ݡ���һ����Ϊ���������ָ�룬�ڶ�����Ϊ�������ݡ���һ����Ϊ8*1024�ֽڣ����Դ洢����8*1024����6���ε�ָ�룬
	 * ÿ��ָ��Ϊ3�ֽ�ͷָ�룬3�ֽ�βָ�롣�ڶ�����Ϊ<=16*1024*1024�ֽڡ�����bin�ļ����ܳ���Ϊ64*N�ֽڣ��������ء����飺�����༭һ����Դ�������������Դ�Ĵ���WAV�ļ����Ϊ
	 * һ��bin�ļ���Ȼ��ʹ�ò���������ظ�bin�ļ���
	 * ������ļ���С���̶�Ϊ�����ֽڣ�����һ���ǿ��Ա�64�ֽ������ġ��ļ���СΪN*64Byte���������ֳ�N�Σ�ÿ��64Byte��ÿ�ζ���˳�� ������š����Ϊ0x000000-0x040000������
	 * Ӧ������ִ��N�Σ����ܴ��������ֿ��ļ����ļ��������(6Byte)=0x3? 0x3? 0x3? 0x3? 0x3? 0x3?�����Ϊ??????����Ӧ����(128Byte)=64�ֽڵ����ݲ��Ϊ128�ֽ�
	 * @param int doc_index �������ָ�룬ȡֵ��Χ0x000000-0x040000
	 * @param byte[] file_64byte_data �ļ����ݣ���С��64�ֽ�
	 */
	public void downloadMusicFile(int doc_index, byte[] file_64byte_data) {
		if((doc_index < 0) || (doc_index > 0x040000)) {return;}
		if(file_64byte_data.length != 64) {return;}
		getSendDataHead((byte)0x41, (byte)(0x36));
		byte dat = (byte)(doc_index >>> 16);
		getSendDataUnboxing(dat);
		dat = (byte)(doc_index >>> 8);
		getSendDataUnboxing(dat);
		dat = (byte)(doc_index);
		getSendDataUnboxing(dat);
		for(int i = 0; i < file_64byte_data.length; i++) {
			getSendDataUnboxing(file_64byte_data[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���ع�˾���Ƶ绰������ʾ����ʾ����������ʾ
	 * @param	int 	disp_Num �����кţ�ȡֵ1-4����=4��Ϊ��ʾ����4��
	 * @param	String 	disp_Data ��ʾ����
	 */
	public void setCompanyName(int disp_Num, String disp_Data) {
		if((disp_Num < 1) || (disp_Num > 4)) return;
		if(disp_Data.length() > 100) return;
		getSendDataHead((byte)0x42, (byte)(0x30 + disp_Num - 1));
		try {
			byte[] myByte;
			myByte = disp_Data.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ����ֽƱ��ӡ���ݣ�����ʾ����ʾ��������ʾ
	 * @param	int hang �кţ�ȡֵ��Χ0-15
	 * @param	int ziTi ����Ŵ�ȡֵ��Χ0-3
	 * @param	int duiQi ���뷽ʽ��ȡֵ��Χ0-2
	 * @param	String printData ��ӡ����
	 */
	public void setPrintData(int hang, int ziTi, int duiQi, String printData) {
		if((hang < 0) || (hang > 15)) {return;}
		if((ziTi < 0) || (ziTi > 3)) {return;}
		if((duiQi < 0) || (duiQi > 2)) {return;}
		getSendDataHead((byte)0x42, (byte)0x35);
		getSendData1Byte((byte)(0x30 + hang));
		getSendData1Byte((byte)(0x30 + ziTi));
		getSendData1Byte((byte)(0x30 + duiQi));
		try {
			byte[] myByte;
			myByte = printData.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡֽƱ��ӡ���ݣ�����ʾ����ʾ��������ʾ
	 * @param	int control ���ư��Զ���Ʊ��־λ��ȡֵ��Χ0-15
	 */
	public void readPrintData(int control) {
		if((control < 0) || (control > 15)) return;
		getSendDataHead((byte)0x42, (byte)0x3D);
		getSendData1Byte((byte)(0x30 + control));
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���ؿ��ư��Զ���Ʊ��־������ʾ����ʾ��������ʾ
	 * @param	int control ���ư��Զ���Ʊ��־λ��ȡֵ��Χ0-3
	 * 						=0x30������ư��Զ���Ʊ(д��flash)��
	 * 						=0x31��ֹ���ư��Զ���Ʊ(д��flash)��
	 * 						=0x32���ı��־λ״̬������֪ͨ���ư��յ��볡Ʊ�ţ�Ҫ����ư�ֹͣ�ϴ�Ʊ�ţ�
	 * 						=0x33���ı��־λ״̬��������ʱ֪ͨ���ư���Գ�һ��Ʊ���ù��ܽ����ڽ�ֹ���ư��Զ���Ʊʱ����ʱ�����Ʊһ�ţ�
	 */
	public void controlPrintTicket(int control, int cheLei, int cheXing, String carNo) {
		if((control < 0) || (control > 3)) return;
		getSendDataHead((byte)0x42, (byte)0x36);
		getSendData1Byte((byte)(0x30 + control));
		if(control == 3) {
			getSendData1Byte((byte)(0x30 + cheLei));
			getSendData1Byte((byte)(0x30 + cheXing));
			getSendDataCarNo(carNo);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡƱ�볡��ͬʱ����ʾ����ʾ��������ʾ
	 */
	public void takeTicketToIn() {
		getSendDataHead((byte)0x42, (byte)0x37);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ˢֽƱ������ͬʱ����ʾ����ʾ��������ʾ
	 */
	public void scanTicketToOut() {
		getSendDataHead((byte)0x42, (byte)0x38);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ֽƱδ�������ֻ�΢��ɨ��ֽƱ���ѣ�ͬʱ����ʾ����ʾ��������ʾ
	 */
	public void ticketPayPlease() {
		getSendDataHead((byte)0x42, (byte)0x39);
		getSendDataEnd();
		send();
	}
	

	/**
	 * ��ʱδ����������ֻ�΢��ɨ��ֽƱ�����ѣ�ͬʱ����ʾ����ʾ��������ʾ
	 */
	public void ticketPayMore() {
		getSendDataHead((byte)0x42, (byte)0x3A);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * ������ʱ�������ݺ��л���������ʾ����ʾ��������ʾ
	 * @param int hangHao �к�ȡֵ1-4��0x31��ʾ��һ�㣻=0x32��ʾ�ڶ��㣻0x33��ʾ�����㣻0x34��ʾ���Ĳ�
	 * @param int xuHao ��Ŀ��ţ�ȡֵ��Χ0-15����Ŀ�л�˳���0x30->0x3f��ע���м䲻�����пյ�
	 * @param int leiXing ��Ŀ���ͣ�ȡֵ��Χ0-8��
	 * 								=0x30��ʾ�����ա�17-08-31����
	 * 								=0x31��ʾ������ʱ�����ڡ�2017��08��31��18ʱ24�� �����ġ���
	 * 								=0x32��ʾ�ꡰ 2017�� ����
	 * 								=0x33��ʾ���ա�08��31�ա���
	 * 								=0x34��ʾʱ�֡�14ʱ35�֡���
	 * 								=0x35��ʾ���ڡ� ������ ����
	 * 								=0x36��ʾʱ���롰14:35:26����
	 * 								=0x37��ʾʣ�೵λ����λ:999���򡰳�λ��������
	 * 								=0x38��ʾ�Զ�������
	 * @param int dispType ��ʾ��ʽ=0������ʾ��=1������ʾ��=2ѭ������ 
	 * @param double time ��ʾʱ����ȡֵ��Χ0.0-655.35��
	 * @param int color ��ɫ��ȡֵ��Χ0-2��=0x30��ɫ��=0x31��ɫ��=0x32��ɫ
	 * @param String dat �Զ����Ŀ���ݣ�����Ŀ����Ϊ�Զ�������ʱ������!=0������Ŀ����Ϊ����ʱ������=0��
	 */
	public void setDisplayData(int hangHao, int xuHao, int leiXing, int dispType, double time, int color, String dat) {
		if((hangHao < 1) || (hangHao > 4)) return;
		if((xuHao < 0) || (xuHao > 15)) return;
		if((leiXing < 0) || (leiXing > 8)) return;
		if((dispType < 0) || (dispType > 2)) return;
		if((time < 0.0) || (time > 655.35)) return;
		if((color < 0) || (color > 2)) return;
		getSendDataHead((byte)0x42, (byte)0x3B);
		getSendData1Byte((byte)(0x30 + hangHao));
		getSendData1Byte((byte)(0x30 + xuHao));
		getSendData1Byte((byte)(0x30 + leiXing));
		getSendData1Byte((byte)(0x30 + dispType));
		int a = (int)(time * 100);
		byte a1 = (byte)(a >>> 8);
		getSendDataUnboxing(a1);
		a1 = (byte)a;
		getSendDataUnboxing(a1);
		getSendData1Byte((byte)(0x30 + color));
		try {
			byte[] bytes = dat.getBytes("GB2312");
			for(int i = 0; i < bytes.length; i++) {
				getSendData1Byte(bytes[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ��ʱ�������ݺ��л���������ʾ����ʾ��������ʾ
	 */
	public void readDisplayData(int hangHao, int xuHao) {
		if((hangHao < 1) || (hangHao > 4)) return;
		if((xuHao < 0) || (xuHao > 15)) return;
		getSendDataHead((byte)0x42, (byte)0x3C);
		getSendData1Byte((byte)(0x30 + hangHao));
		getSendData1Byte((byte)(0x30 + xuHao));
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���ش�ӡ���ؼ��ʣ�����ʾ����ʾ��������ʾ
	 * @param	String	projectNum ��Ŀ��ţ���4λ�ַ���ɣ�ÿ���ַ�Ϊ0123456789ABCDEF֮һ
	 * @param	String	roadNum �����ţ���2λ�ַ���ɣ�ÿ���ַ�Ϊ0123456789ABCDEF֮һ
	 * @param	String	carType �������ͣ���1λ�ַ���ɣ�ÿ���ַ�Ϊ0123456789ABCDEF֮һ
	 * @param	String	url ��ַǰ׺��0<����<200
	 */
	public void setPrinterParameter(String projectNum, String roadNum, String carType, String url) {
		if(projectNum.length() != 4) return;
		if(roadNum.length() != 2) return;
		if(carType.length() != 1) return;
		if((url.length() == 0) || (url.length() >= 200)) return;
		getSendDataHead((byte)0x42, (byte)0x3E);
		try {
			byte[] tmp1 = projectNum.getBytes("GB2312");
			for(int i = 0; i < tmp1.length; i++) {
				getSendData1Byte(tmp1[i]);
			}
			byte[] tmp2 = roadNum.getBytes("GB2312");
			for(int i = 0; i < tmp2.length; i++) {
				getSendData1Byte(tmp2[i]);
			}
			byte[] tmp5 = roadNum.getBytes("GB2312");
			getSendData1Byte(tmp5[0]);
			byte[] tmp3 = url.getBytes("GB2312");
			for(int i = 0; i < tmp3.length; i++) {
				getSendData1Byte(tmp3[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ��ӡ���ؼ��ʣ�����ʾ����ʾ��������ʾ
	 */
	public void readPrinterParameter() {
		getSendDataHead((byte)0x42, (byte)0x3F);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȴ�ȷ�ϣ�ͬʱ����ʾ����ʾ��������ʾ
	 */
	public void pleaseWait() {
		getSendDataHead((byte)0x43, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * �˻�δע�ᣬͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void unRegistered(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x31);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * �˻�δ��Ȩ��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void unAuthorization(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x32);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * �˻������ã�ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void beDisabled(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x33);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * �˻��ѹ��ڣ�ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void datePassed(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x34);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * �˻����㣬ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void noEnoughMoney(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x35);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * �����볡��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void carAlreadyIn(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x36);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	
	/**
	 * ��δ�볡��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void carNotIn(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x37);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * �����բ/��բ��ͬʱ����ʾ����ʾ
	 * @param	boolean open_true Ϊtrue��բ��Ϊfalse����բ 
	 */
	public void softwareOpenCloseDoor(boolean open_true) {
		if(open_true) {
			getSendDataHead((byte)0x43, (byte)0x40);
		} else {
			getSendDataHead((byte)0x43, (byte)0x41);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���볡ͣ����ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void pleaseComeIn(String carNo, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3A);
		getSendDataCarNo(carNo);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���볡ͣ����ʣ��ʹ������XXXXXX�죬ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	int 	date_numberʣ��ʹ�����ڣ�����=15����ʣ��ʹ������15��
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void pleaseComeIn_RemainDate(String carNo, int date_number, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3B);
		getSendDataCarNo(carNo);
		try {
			byte[] date;
			date = Integer.toString(date_number).getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 6) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���볡ͣ����ʣ����SXXXXX.xxԪ��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	double 	money_numberʣ�������=-300.06����ʣ���300.06Ԫ
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void pleaseComeIn_RemainMoney(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x3C, carNo, money_number, carType, carClass);
	}
	
	/**
	 * ף��һ·ƽ����ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void goodBye(String carNo, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3D);
		getSendDataCarNo(carNo);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ף��һ·ƽ����ʣ��ʹ������XXXXXX�죬ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	int 	date_numberʣ��ʹ�����ڣ�����=15����ʣ��ʹ������15��
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void goodBye_RemainDate(String carNo, int date_number, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x3E);
		getSendDataCarNo(carNo);
		try {
			byte[] date;
			date = Integer.toString(date_number).getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 6) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ף��һ·ƽ����ʣ����SXXXXX.xxԪ��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	double 	money_numberʣ�������=-300.06����ʣ���300.06Ԫ
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void goodBye_RemainMoney(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x3F, carNo, money_number, carType, carClass);
	}
	
	/**
	 * �뽻��SXXXXX.xxԪ��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	double 	money_number���ѽ�����=128.36�����뽻��128.36Ԫ
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void pleasePay(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x42, carNo, money_number, carType, carClass);
	}
	
	/**
	 * �Բ����˻��ѹ��ڣ��뽻��SXXXXX.xxԪ��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	double 	money_number���ѽ�����=128.36�����뽻��128.36Ԫ
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void datePassed_PleasePay(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x43, carNo, money_number, carType, carClass);
	}
	
	/**
	 * �Բ����˻����㣬�뽻��SXXXXX.xxԪ��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	double 	money_number���ѽ�����=128.36�����뽻��128.36Ԫ
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void noEnoughMoney_PleasePay(String carNo, double money_number, byte carType, byte carClass) {
		getSendDataCarNo_Money_CarType_CarClass((byte)0x43, (byte)0x44, carNo, money_number, carType, carClass);
	}
	
	/**
	 * ��λ����������ʾ����ʾ��������ʾ
	 */
	public void lotsFull() {
		getSendDataHead((byte)0x43, (byte)0x45);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��λ���࣬����ʾ����ʾ��������ʾ
	 * @param	int 	lots_Numberʣ�೵λ���������ܴ���9999
	 */
	public void lotsNumber(int lots_Number) {
		getSendDataHead((byte)0x43, (byte)0x46);
		if(lots_Number > 9999) lots_Number = 9999;
		try {
			byte[] date;
			date = Integer.toString(lots_Number).getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 4) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * �����ѳ�ʱ���뵽������������ѣ�ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void payTimeOver(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x4A);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��δ���ѣ����ȵ������շѴ����ѣ�ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void pleasePayFirst(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x4B);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}
	

	/**
	 * �뵽�����������ڣ�ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 */
	public void pleaseGoToManagementAndPay(String carNo) {
		getSendDataHead((byte)0x43, (byte)0x4C);
		getSendDataCarNo(carNo);
		getSendDataEnd();
		send();
	}


	/**
	 * ͣ��XX��XXСʱXX�֣��뽻�ѣ�SXXXXX.xxԪ��ͬʱ����ʾ����ʾ��������ʾ
	 * @param	String	carNo ���ƺ���
	 * @param	int 	pDay ͣ��ʱ��������
	 * @param	int 	pHour ͣ��ʱ����Сʱ��
	 * @param	int 	pMinute ͣ��ʱ���ķ�����
	 * @param	double 	money_number ͣ������
	 * @param	byte	carType ���ͣ�0x41��ʱ����0x42���⳵��0x43��ֵ����0x44��ѳ�
	 * @param	byte	carClass ���ࣺ0x41����A��0x42����B��0x43����C��0x44����D
	 */
	public void parkTimeAndPleasePay(String carNo, int pDay, int pHour, int pMinute, double money_number, byte carType, byte carClass) {
		getSendDataHead((byte)0x43, (byte)0x4D);
		getSendDataCarNo(carNo);
		if(pDay > 99) pDay = 99;
		byte[] date1 = Integer.toString(pDay).getBytes();
		int i = 0;
		while((date1.length + i) < 2) {
			getSendData1Byte((byte)0x30);
			i++;
		}
		for(i = 0; i < date1.length; i++) {
			getSendData1Byte(date1[i]);
		}
		if(pHour > 99) pHour = 99;
		byte[] date2 = Integer.toString(pHour).getBytes();
		i = 0;
		while((date2.length + i) < 2) {
			getSendData1Byte((byte)0x30);
			i++;
		}
		for(i = 0; i < date2.length; i++) {
			getSendData1Byte(date2[i]);
		}
		if(pMinute > 99) pMinute = 99;
		byte[] date3 = Integer.toString(pMinute).getBytes();
		i = 0;
		while((date3.length + i) < 2) {
			getSendData1Byte((byte)0x30);
			i++;
		}
		for(i = 0; i < date3.length; i++) {
			getSendData1Byte(date3[i]);
		}
		getSendDataMoney(money_number);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ������������������ʾ����ʾ��������ʾ
	 * @param	int	volume ������ȡֵ��Χ0-6
	 */
	public void changeVolume(int volume) {
		getSendDataHead((byte)0x43, (byte)0x4E);
		switch(volume) {
		case 0:getSendData1Byte((byte)0xe0);break;
		case 1:getSendData1Byte((byte)0xe1);break;
		case 2:getSendData1Byte((byte)0xe2);break;
		case 3:getSendData1Byte((byte)0xe3);break;
		case 4:getSendData1Byte((byte)0xe4);break;
		case 5:getSendData1Byte((byte)0xe5);break;
		case 6:getSendData1Byte((byte)0xe6);break;
		default:getSendData1Byte((byte)0xe6);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * �Բ��𣬳��ƺ�ʶ�������ȡ���볡(ͬʱ����3#�̵���)��ͬʱ����ʾ����ʾ��������ʾ
	 */
	public void carNoErr_PleaseTakeCard() {
		getSendDataHead((byte)0x43, (byte)0x4F);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���ĳ�λ��ռ�ã�������ʱ���볡��ͬʱ����ʾ����ʾ��������ʾ
	 */
	public void comeInAsGuest() {
		getSendDataHead((byte)0x43, (byte)0x50);
		getSendDataEnd();
		send();
	}
	
	/**
	 * �Ƿ��Զ��л�������ʾ����ʾ��������ʾ
	 * @param	int 	disp_Num�����кţ�ȡֵ1-4����=4��Ϊ��ʾ����4��
	 * @param	boolean	autoChange_true�Զ��л���־��=true���Զ��л���ʱ���ݣ�=false��������ǰ���ݣ��������л�
	 */
	public void autoChange(int disp_Num, boolean autoChange_true) {
		switch(disp_Num) {
		case 1:getSendDataHead((byte)0x45, (byte)0x30);break;
		case 2:getSendDataHead((byte)0x45, (byte)0x31);break;
		case 3:getSendDataHead((byte)0x45, (byte)0x32);break;
		case 4:getSendDataHead((byte)0x45, (byte)0x33);break;
		default:getSendDataHead((byte)0x45, (byte)0x33);
		}
		if(autoChange_true) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��λ���ư壬���ư彫��λ
	 */
	public void resetBoard() {
		getSendDataHead((byte)0x46, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ʾ��ά����ɨ���볡������ʾ����ʾ����������ʾ
	 * @param	int		hangHao��ʾ���кţ�ȡֵ��Χ1-4
	 * @param	boolean	xuHao��ţ�ÿ�з�Ϊ2֡���ݣ���һ�����Ϊtrue���ڶ������Ϊfalse
	 * @param	byte[]	bitData��ά�����ݣ����鳤�ȱ���Ϊ128byte
	 */
	public void displayQRCode(int hangHao, boolean xuHao, byte[] bitData) {
		if(bitData.length != 128) return;
		if((hangHao > 4) || (hangHao < 1)) return;
		getSendDataHead((byte)0x46, (byte)0x31);
		switch(hangHao) {
		case 1:getSendData1Byte((byte)0x30);break;
		case 2:getSendData1Byte((byte)0x31);break;
		case 3:getSendData1Byte((byte)0x32);break;
		case 4:getSendData1Byte((byte)0x33);break;
		}
		if(xuHao) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		for(int i = 0; i < bitData.length; i++) {
			getSendDataUnboxing(bitData[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ���ƿ���������ʾ����ʾ��������ʾ
	 * @param	boolean	out_true__in_false���ƿ�����־��=true����ƿ���������=false����ƿ����̿�
	 */
	public void controlCardMachine(boolean out_true__in_false) {
		getSendDataHead((byte)0x46, (byte)0x32);
		if(out_true__in_false) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ʾ����ʾ��ʱ��Ϣ������ʾ����ʾ����������ʾ
	 * @param	int 	disp_Num�����кţ�ȡֵ1-4����=4��Ϊ��ʾ����4��
	 * @param	int		disp_Type��ʾ��ʽ=0������ʾ��=1������ʾ��=2ѭ�����ƣ�=3������ʾ���ҽ�ֹ�л���=4������ʾ���ҽ�ֹ�л���=5ѭ�����Ʋ��ҽ�ֹ�л���
	 * @param	String 	disp_Data��ʾ����
	 */
	public void displayTempMessage(int disp_Num, int disp_Type, String disp_Data) {
		if((disp_Num < 1) || (disp_Num > 4)) return;
		if((disp_Type < 0) || (disp_Type > 5)) return;
		if(disp_Data.length() > 100) return;
		getSendDataHead((byte)0x48, (byte)(0x30 + disp_Num));
		getSendData1Byte((byte)(0x30 + disp_Type));
		try {
			byte[] myByte;
			myByte = disp_Data.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��������ָ���ļ̵���������ʾ����ʾ��������ʾ
	 * @param	byte	relay���ϵļ̵������ƣ�bit0-bit3�ֱ����4���̵�����=1��ʾ���ϣ�=0��ʾ�����ƣ���������1#��������relay=0x01
	 */
	public void controlRelayAction(byte relay) {
		getSendDataHead((byte)0x49, (byte)0x30);
		getSendDataUnboxing(relay);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��������ָ���ļ̵���������ָ����ʱ����ͷţ�����ʾ����ʾ��������ʾ
	 * @param	byte	relay���ϵļ̵������ƣ�bit0-bit3�ֱ����4���̵�����=1��ʾ���ϣ�=0��ʾ�����ƣ���������1#��������relay=0x01
	 * @param	double[] relayTime���ϵ�ʱ�䣬���鳤��Ϊ4��ȡֵΪ0.0-25.5����λΪ��
	 */
	public void controlRelayAction_TimeOverFree(byte relay, double[] relayTime) {
		if(relayTime.length != 4) return;
		for(int i = 0; i < relayTime.length; i++) {
			if((relayTime[i] < 0.0) || (relayTime[i] > 25.5)) return;
		}
		getSendDataHead((byte)0x49, (byte)0x31);
		for(int i = 0; i < relayTime.length; i++) {
			if((byte)(relay & 0x01) == (byte)0x01) {
				getSendData1Byte((byte)0x31);
			} else {
				getSendData1Byte((byte)0x30);
			}
			getSendDataUnboxing((byte)(relayTime[i] * 10));
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * �����ͷ�ָ���ļ̵���������ʾ����ʾ��������ʾ
	 * @param	byte	relay�ͷŵļ̵������ƣ�bit0-bit3�ֱ����4���̵�����=1��ʾ�ͷţ�=0��ʾ�����ƣ������ͷ�1#��������relay=0x01
	 */
	public void controlRelayFree(byte relay) {
		getSendDataHead((byte)0x4A, (byte)0x30);
		getSendDataUnboxing(relay);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ���ư����кţ�����ʾ����ʾ��������ʾ
	 */
	public void readSerialNumber() {
		getSendDataHead((byte)0x4B, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ����ϵͳȨ�ޣ�����ʾ����ʾ��������ʾ
	 */
	public void copyRight() {
		getSendDataHead((byte)0x4C, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��������ָ�����ݣ�����ʾ����ʾ����������ʾ
	 * @param	byte[] 	musicNumberArray�����Ķκţ�ȡֵ0x00-0xbf�����鳤�Ȳ�����35
	 */
	public void playMusicArray(byte[] musicNumberArray) {
		if(musicNumberArray.length > 35) return;
		getSendDataHead((byte)0x4D, (byte)0x30);
		for(int i = 0; i < musicNumberArray.length; i++) {
			getSendDataUnboxing(musicNumberArray[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ͸��ת��RS485������ʾ����ʾ��������ʾ
	 * @param	byte[] 	ת�����ݣ����鳤�Ȳ�����100
	 */
	public void sendRs485Array(byte[] rs485Array) {
		if(rs485Array.length > 100) return;
		getSendDataHead((byte)0x4E, (byte)0x30);
		for(int i = 0; i < rs485Array.length; i++) {
			getSendDataUnboxing(rs485Array[i]);
		}
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ�汾�ţ�����ʾ����ʾ��������ʾ
	 */
	public void readVersion() {
		getSendDataHead((byte)0x50, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ���ư�״̬������ʾ����ʾ��������ʾ
	 */
	public void readStatus() {
		getSendDataHead((byte)0x83, (byte)0x30);
		getSendDataEnd();
		send();
	}
	
	/**
	 * ��ȡ���ư�IO������ʾ����ʾ��������ʾ
	 */
	public void readBoardIO() {
		getSendDataHead((byte)0x83, (byte)0x31);
		getSendDataEnd();
		send();
	}
	
	/**
     * ����һ��
     * byte[] to hex string
     * 
     * @param bytes
     * @return
     */
    public static String bytesToHexFun1(byte[] bytes) {
        // һ��byteΪ8λ����������ʮ������λ��ʶ
        char[] buf = new char[bytes.length * 3];
        int a = 0;
        int index = 0;
        for(byte b : bytes) { // ʹ�ó���ȡ�����ת��
            if(b < 0) {
                a = 256 + b;
            } else {
                a = b;
            }

            buf[index++] = HEX_CHAR[a / 16];
            buf[index++] = HEX_CHAR[a % 16];
            buf[index++] = ' ';
        }

        return new String(buf);
    }

	/* �������� */
	private void send() {
		if(SendAddIndex == 0) return;
		byte[] sendbuf = new byte[SendAddIndex];
		for(int i = 0; i < sendbuf.length; i++) {
			sendbuf[i] = SendDataBuf[i];
		}
		RecvMessageArea.append("[Send]=" + bytesToHexFun1(sendbuf));
		SendAddIndex = 0;
		if(isTcpFlag) {//ͨ��TCP��ʽ�����豸
			tcpClient.TcpSendData(sendbuf);
		} else {//ͨ��COM��ʽ�����豸
			rs232.ComSendData(sendbuf);
		}
	}
	
	/* ���뷢������ͷ */
	private void getSendDataHead(byte fc1, byte fc2) {
		SendCrc = 0;
		SendDataBuf[SendAddIndex++] = SEND_HEAD;
		SendCrc ^= SEND_HEAD;
		SendDataBuf[SendAddIndex++] = fc1;
		SendCrc ^= fc1;
		SendDataBuf[SendAddIndex++] = fc2;
		SendCrc ^= fc2;
	}
	/* ���뷢������β */
	private void getSendDataEnd() {
		byte crc1 = 0;
		byte crc2 = 0;
		SendDataBuf[SendAddIndex++] = SEND_ETX;
		SendCrc ^= SEND_ETX;
		crc1 = (byte)((SendCrc >>> 4) | 0x30);
		crc2 = (byte)((SendCrc & 0x0f) | 0x30);
		SendDataBuf[SendAddIndex++] = crc1;
		SendDataBuf[SendAddIndex++] = crc2;
		SendDataBuf[SendAddIndex++] = SEND_END;
	}
	
	/* ���뷢������1���ֽ� */
	private void getSendData1Byte(byte dat) {
		SendDataBuf[SendAddIndex++] = dat;
		SendCrc ^= (byte)dat;
	}
	
	/* ���뷢�����ݲ��1���ֽ�Ϊ2���ֽ� */
	private void getSendDataUnboxing(byte dat) {
		byte tmp = (byte)(((dat & 0xf0) >>> 4) | 0x30);
		getSendData1Byte(tmp);
		tmp = (byte)((dat & 0x0f) | 0x30);
		getSendData1Byte(tmp);
	}
	
	/* ���뷢�����ݷ��ͳ��ƺ���  */
	private void getSendDataCarNo(String carNo) {
		try {
			byte[] myByte;
			myByte = carNo.getBytes("GB2312");
			int i = 0;
			for(; i < myByte.length; i++) {
				getSendData1Byte(myByte[i]);
				if(i == 10) {break;}
			}
			while(i < 10) {
				getSendData1Byte((byte)0x20);
				i++;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/* ���뷢�����ݷ��ͽ��  */
	private void getSendDataMoney(double money_number) {
		if(money_number >= 0.0) {
			getSendData1Byte((byte)0x30);
		} else {
			getSendData1Byte((byte)0x31);
		}
		money_number = Math.abs(money_number);
		String date_str = Integer.toString((int)(money_number * 100));
		try {
			byte[] date;
			date = date_str.getBytes("GB2312");
			int i = 0;
			while((date.length + i) < 7) {
				getSendData1Byte((byte)0x30);
				i++;
			}
			for(i = 0; i < date.length; i++) {
				getSendData1Byte(date[i]);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/* ���뷢�����ݷ��ͽ��  */
	private void getSendDataCarNo_Money_CarType_CarClass(byte fc1, byte fc2, String carNo, double money_number, byte carType, byte carClass) {
		getSendDataHead(fc1, fc2);
		getSendDataCarNo(carNo);
		getSendDataMoney(money_number);
		getSendData1Byte(carType);
		getSendData1Byte(carClass);
		getSendDataEnd();
		send();
	}
	
	/* Index++ */
	private int Index_Inc(int index) {
		index++;
		if(index >= BUF_LENGTH) index = 0;
		return index;
	}
	
	/* ����������� */
	private void Recv(byte[] recvbuf) {
		for(int i = 0; i < recvbuf.length; i++) {//���ո��յ��������������ݻ�����
			if(RecvAddIndex == RecvDoneIndex) {
				RecvAddIndex = 0;
				RecvDoneIndex = 0;
			}
			RecvDataBuf[RecvAddIndex] = recvbuf[i];
			RecvAddIndex = Index_Inc(RecvAddIndex);
		}
		int flag = 0;
		int headIndex = 0;
		int etxIndex = 0;
		byte RecvCrc1 = 0;//����Э���У����1
		byte RecvCrc2 = 0;//����Э���У����2
		RecvCrc = 0;
		while(RecvDoneIndex != RecvAddIndex) {//����������Ϊ��ʱ��һֱ����
			if(flag == 0) {//��0������Э��ͷ
				if(RecvDataBuf[RecvDoneIndex] == RECV_HEAD) {
					headIndex = RecvDoneIndex;
					RecvCrc = 0;
					flag = 1;
				}
				RecvCrc ^= RecvDataBuf[RecvDoneIndex];//����У����
			} else if(flag == 1) {//��1���������ݽ����������������Э��ͷ���������µ�Э��ͷ
				if(RecvDataBuf[RecvDoneIndex] == RECV_HEAD) {
					headIndex = RecvDoneIndex;
					RecvCrc = 0;
				} else if(RecvDataBuf[RecvDoneIndex] == RECV_ETX) {
					etxIndex = RecvDoneIndex;
					flag = 2;
				}
				RecvCrc ^= RecvDataBuf[RecvDoneIndex];//����У����
			} else if(flag == 2) {//��2������У����1
				RecvCrc1 = RecvDataBuf[RecvDoneIndex];//����У����1
				flag = 3;
			} else if(flag == 3) {//��3������У����2�����ж�У�����Ƿ���ȷ
				RecvCrc2 = RecvDataBuf[RecvDoneIndex];//����У����2
				int tmp0 = (RecvCrc >>> 4) & 0x0000000f;
				byte tmp1 = (byte)(tmp0 | 0x30);
				byte tmp2 = (byte)((RecvCrc & 0x0f) | 0x30);
				if(tmp1 == RecvCrc1 && tmp2 == RecvCrc2) {
					flag = 4;
				} else {
					flag = 0;
				}
			} else {//��4������Э��β��������һ�������Э������
				if(RecvDataBuf[RecvDoneIndex] == RECV_END) {
					//���ݹ��������Э��
					getCommandBack(headIndex, etxIndex);
					printlnRecvData(headIndex, etxIndex);
				}
				flag = 0;
			}
			RecvDoneIndex = Index_Inc(RecvDoneIndex);
		}
		RecvMessageArea.append(RecvDataString);
		RecvDataString = "";
		scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());//���ƴ�ֱ�������������
	}
	
	private void printlnRecvData(int headIndex, int etxIndex) {
		int count = 0;
		int head_tmp = headIndex;
		while(head_tmp != etxIndex) {
			count++;
			head_tmp = Index_Inc(head_tmp);
		}
		count += 4;
		byte[] recv_data = new byte[count];
		for(int i = 0; i < recv_data.length; i++) {
			recv_data[i] = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
		}
		RecvMessageArea.append("[Recv]=" + bytesToHexFun1(recv_data));
	}

	/* ������յ����������ݹ���������յ���Э��  */
	private void getCommandBack(int headIndex, int etxIndex) {
		headIndex = Index_Inc(headIndex);
		RecvFc1 = RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvFc2 = RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		switch(RecvFc1) {
		case (byte)0x41:getCommandBack_41(headIndex, etxIndex);break;
		case (byte)0x42:getCommandBack_42(headIndex, etxIndex);break;
		case (byte)0x43:getCommandBack_43(headIndex, etxIndex);break;
		case (byte)0x45:getCommandBack_45(headIndex, etxIndex);break;
		case (byte)0x46:getCommandBack_46(headIndex, etxIndex);break;
		case (byte)0x48:getCommandBack_48(headIndex, etxIndex);break;
		case (byte)0x49:getCommandBack_49(headIndex, etxIndex);break;
		case (byte)0x4A:RecvDataString += "�ͷż̵�������ִ�гɹ�\r\n";break;
		case (byte)0x4B:getCommandBack_4B(headIndex, etxIndex);break;
		case (byte)0x4C:RecvDataString += "����ϵͳȨ������ִ�гɹ�\r\n";break;
		case (byte)0x4D:RecvDataString += "��������ָ����������ִ�гɹ�\r\n";break;
		case (byte)0x4E:RecvDataString += "͸��ת��RS485��������ִ�гɹ�\r\n";break;
		case (byte)0x4F:getCommandBack_4F(headIndex, etxIndex);break;
		case (byte)0x50:getCommandBack_50(headIndex, etxIndex);break;
		case (byte)0x61:getCommandBack_61(headIndex, etxIndex);break;
		case (byte)0x62:RecvDataString += "���ư��Ѹ�λ\r\n";break;
		case (byte)0x71:RecvDataString += "�����ź�IN1���޵��У������ظУ�\r\n";break;
		case (byte)0x72:RecvDataString += "�����ź�IN1���е��ޣ������ظУ�\r\n";break;
		case (byte)0x73:RecvDataString += "�����ź�IN2���޵��У����ҵظУ�\r\n";break;
		case (byte)0x74:RecvDataString += "�����ź�IN2���е��ޣ����ҵظУ�\r\n";break;
		case (byte)0x75:RecvDataString += "�����ź�IN3���޵��У�Ʊ���Ŵţ�\r\n";break;
		case (byte)0x76:RecvDataString += "�����ź�IN3���е��ޣ�Ʊ���Ŵţ�\r\n";break;
		case (byte)0x77:RecvDataString += "�����ź�IN4���޵��У�ȡƱ��ť��\r\n";break;
		case (byte)0x78:RecvDataString += "�����ź�IN4���е��ޣ�ȡƱ��ť��\r\n";break;
		case (byte)0x79:RecvDataString += "�����ź�WGB_D0���޵��У���բ����λ��\r\n";break;
		case (byte)0x7A:RecvDataString += "�����ź�WGB_D0���е��ޣ���բ����λ��\r\n";break;
		case (byte)0x7B:RecvDataString += "�����ź�WGB_D1���޵��У���բ�䵽λ��\r\n";break;
		case (byte)0x7C:RecvDataString += "�����ź�WGB_D1���е��ޣ���բ�䵽λ��\r\n";break;
		case (byte)0x7D:RecvDataString += "�����ź�WGA_D0���޵��У���բ�Ŵţ�\r\n";break;
		case (byte)0x7E:RecvDataString += "�����ź�WGA_D0���е��ޣ���բ�Ŵţ�\r\n";break;
		case (byte)0x7F:RecvDataString += "�����ź�WGA_D1���޵��У���բ�˱�ײ��\r\n";break;
		case (byte)0x80:RecvDataString += "�����ź�WGA_D1���е��ޣ���բ�˱�ײ��\r\n";break;
		case (byte)0x82:getCommandBack_82(headIndex, etxIndex);break;
		case (byte)0x83:getCommandBack_83(headIndex, etxIndex);break;
		case (byte)0x84:getCommandBack_84(headIndex, etxIndex);break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}
	
	private void getCommandBack_41(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "У��ʱ������ִ�гɹ�\r\n";break;
		case (byte)0x31:RecvDataString += "���ÿ��ư�ģʽ����������ִ�гɹ�\r\n";break;
		case (byte)0x32:getCommandBack_4132(headIndex, etxIndex);break;
		case (byte)0x33:RecvDataString += "�����ֿ��ļ�����ִ�гɹ�\r\n";break;
		case (byte)0x34:RecvDataString += "����������������ִ�гɹ�\r\n";break;
		case (byte)0x35:RecvDataString += "�����Զ�����ʱ������ִ�гɹ�\r\n";break;
		case (byte)0x36:RecvDataString += "������Դ����ִ�гɹ�\r\n";break;
		case (byte)0x37:getCommandBack_4137(headIndex, etxIndex);break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}
	
	/* ��ȡ���ư�ģʽ�����Ρ�������ֵ���� */
	private void getCommandBack_4132(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ���ư�ģʽ����������ִ�гɹ���";
		switch(RecvDataBuf[headIndex]) {//���ư�ģʽ
		case (byte)0x30:RecvDataString += "���ư�ģʽ=CP200Nģʽ��";break;
		case (byte)0x31:RecvDataString += "���ư�ģʽ=CP200N��һ���������";break;
		case (byte)0x32:RecvDataString += "���ư�ģʽ=CP200N��ģ��621ģʽ��";break;
		case (byte)0x33:RecvDataString += "���ư�ģʽ=CP200N�ѻ��շ�ģʽ��";break;
		default:RecvDataString += "���ư�ģʽ=δ֪ģʽ��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//���������
		case (byte)0x30:RecvDataString += "�������������=7098SY��";break;
		case (byte)0x31:RecvDataString += "�������������=1���������";break;
		case (byte)0x32:RecvDataString += "�������������=7182SY��";break;
		case (byte)0x33:RecvDataString += "�������������=7183SY��";break;
		default:RecvDataString += "�������������=" + RecvDataBuf[headIndex] + "���������";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//ɨ�跽ʽ
		case (byte)0x30:RecvDataString += "ɨ�跽ʽ=16ɨ��ʽ0��";break;
		case (byte)0x31:RecvDataString += "ɨ�跽ʽ=16ɨ��ʽ1��δʵ�֣���";break;
		case (byte)0x32:RecvDataString += "ɨ�跽ʽ=8ɨ��ʽ0��";break;
		case (byte)0x33:RecvDataString += "ɨ�跽ʽ=8ɨ��ʽ1��δʵ�֣���";break;
		case (byte)0x34:RecvDataString += "ɨ�跽ʽ=4ɨ��ʽ0��";break;
		case (byte)0x35:RecvDataString += "ɨ�跽ʽ=4ɨ��ʽ1��δʵ�֣���";break;
		default:RecvDataString += "ɨ�跽ʽ=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//��Ļ�߶�
		case (byte)0x30:RecvDataString += "��Ļ�߶�=һ�к��֣�";break;
		case (byte)0x31:RecvDataString += "��Ļ�߶�=���к��֣�";break;
		case (byte)0x32:RecvDataString += "��Ļ�߶�=���к��֣�";break;
		case (byte)0x33:RecvDataString += "��Ļ�߶�=���к��֣�";break;
		default:RecvDataString += "��Ļ�߶�=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//��Ļ���
		case (byte)0x30:RecvDataString += "��Ļ���=4���ֿ�ȣ�";break;
		case (byte)0x31:RecvDataString += "��Ļ���=5���ֿ�ȣ�δʵ�֣���";break;
		case (byte)0x32:RecvDataString += "��Ļ���=8���ֿ�ȣ�";break;
		default:RecvDataString += "��Ļ���=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//OE����
		case (byte)0x30:RecvDataString += "OE����=�����ԣ�";break;
		case (byte)0x31:RecvDataString += "OE����=�����ԣ�";break;
		default:RecvDataString += "OE����=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//���ݼ���
		case (byte)0x30:RecvDataString += "���ݼ���=�����ԣ�";break;
		case (byte)0x31:RecvDataString += "���ݼ���=�����ԣ�";break;
		default:RecvDataString += "���ݼ���=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//ɫ������
		case (byte)0x30:RecvDataString += "ɫ������=��ɫ��";break;
		case (byte)0x31:RecvDataString += "ɫ������=˫��ɫ��";break;
		case (byte)0x32:RecvDataString += "ɫ������=ȫ�ʣ�δʵ�֣���";break;
		default:RecvDataString += "ɫ������=δ֪��";
		}
		RecvDataString += "\r\n";
	}
	
	/* ��ȡ����������������ֵ���� */
	private void getCommandBack_4137(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ������������ִ�гɹ���";
		switch(RecvDataBuf[headIndex]) {//�����������ƺ���
		case (byte)0x30:RecvDataString += "�����������ƺ���=���������ƣ�";break;
		case (byte)0x31:RecvDataString += "�����������ƺ���=�������ƣ�";break;
		default:RecvDataString += "�����������ƺ���=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//3�ż̵����Զ����ƺ��̵�
		case (byte)0x30:RecvDataString += "3�ż̵����Զ����ƺ��̵�=�Զ����ƣ�";break;
		case (byte)0x31:RecvDataString += "3�ż̵����Զ����ƺ��̵�=��λ��������ƣ�";break;
		default:RecvDataString += "3�ż̵����Զ����ƺ��̵�=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		if(RecvDataBuf[headIndex] != (byte)0x40) {//����û�����������ˣ���Ӧ���˳�
			RecvDataString += "\r\n";
			return;
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//��λ�ӿ�͸��ת����λ������
		case (byte)0x30:RecvDataString += "��λ�ӿ�͸��ת����λ������=��Ҫת����";break;
		case (byte)0x31:RecvDataString += "��λ�ӿ�͸��ת����λ������=��ת����";break;
		default:RecvDataString += "��λ�ӿ�͸��ת����λ������=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//����AB�ӿڶ�������
		case (byte)0x30:RecvDataString += "����AB�ӿڶ�������=���֣�";break;
		case (byte)0x31:RecvDataString += "����AB�ӿڶ�������=�����֣�";break;
		default:RecvDataString += "����AB�ӿڶ�������=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		if(RecvDataBuf[headIndex] != (byte)0x40) {//����û�����������ˣ���Ӧ���˳�
			RecvDataString += "\r\n";
			return;
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//������־
		case (byte)0x30:RecvDataString += "������־=������";break;
		case (byte)0x31:RecvDataString += "������־=������";break;
		default:RecvDataString += "������־=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//�ߵ���־
		case (byte)0x30:RecvDataString += "�ߵ���־=�����ߵ���";break;
		case (byte)0x31:RecvDataString += "������־=���ߵ���";break;
		default:RecvDataString += "������־=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		if(RecvDataBuf[headIndex] != (byte)0x40) {//����û�����������ˣ���Ӧ���˳�
			RecvDataString += "\r\n";
			return;
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//ɨ��ǹ���ڽӿ�����־
		case (byte)0x30:RecvDataString += "ɨ��ǹ���ڽӿ�����־=���ڽ�ɨ��ǹ��";break;
		case (byte)0x31:RecvDataString += "ɨ��ǹ���ڽӿ�����־=���ڽӿ�����";break;
		default:RecvDataString += "ɨ��ǹ���ڽӿ�����־=δ֪��";
		}
		RecvDataString += "\r\n";
	}
	
	private void getCommandBack_42(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "���ع�˾���Ƶ绰��һ�㣩����ִ�гɹ�\r\n";break;
		case (byte)0x31:RecvDataString += "���ع�˾���Ƶ绰�����㣩����ִ�гɹ�\r\n";break;
		case (byte)0x32:RecvDataString += "���ع�˾���Ƶ绰�����㣩����ִ�гɹ�\r\n";break;
		case (byte)0x33:RecvDataString += "���ع�˾���Ƶ绰���Ĳ㣩����ִ�гɹ�\r\n";break;
		case (byte)0x35:RecvDataString += "����ֽƱ��ӡ��������ִ�гɹ�\r\n";break;
		case (byte)0x36:RecvDataString += "���ؿ��ư��Զ���Ʊ��־����ִ�гɹ�\r\n";break;
		case (byte)0x37:RecvDataString += "��ȡƱ�볡����ִ�гɹ�\r\n";break;
		case (byte)0x38:RecvDataString += "��ˢֽƱ��������ִ�гɹ�\r\n";break;
		case (byte)0x39:RecvDataString += "ֽƱδ�������ֻ�΢��ɨ��ֽƱ��������ִ�гɹ�\r\n";break;
		case (byte)0x3A:RecvDataString += "��ʱδ����������ֻ�΢��ɨ��ֽƱ����������ִ�гɹ�\r\n";break;
		case (byte)0x3B:RecvDataString += "������ʱ�������ݺ��л���������ִ�гɹ�\r\n";break;
		case (byte)0x3C:getCommandBack_423C(headIndex, etxIndex);break;
		case (byte)0x3D:getCommandBack_423D(headIndex, etxIndex);break;
		case (byte)0x3E:RecvDataString += "���ش�ӡ���ؼ�������ִ�гɹ�\r\n";break;
		case (byte)0x3F:getCommandBack_423F(headIndex, etxIndex);break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}
	
	/* ��ȡ��ʱ�������ݺ��л����򡪡�����ֵ���� */
	private void getCommandBack_423C(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ��ʱ�������ݺ��л���������ִ�гɹ���";
		RecvDataString += "�к�=" + (char)RecvDataBuf[headIndex] + "��";
		headIndex = Index_Inc(headIndex);
		RecvDataString += "��Ŀ���=" + (char)RecvDataBuf[headIndex] + "��";
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//��Ŀ����
		case (byte)0x30:RecvDataString += "��Ŀ����=�����ա�17-08-31����";break;
		case (byte)0x31:RecvDataString += "��Ŀ����=������ʱ�����ڡ�2017��08��31��18ʱ24�� �����ġ���";break;
		case (byte)0x32:RecvDataString += "��Ŀ����=�ꡰ 2017�� ����";break;
		case (byte)0x33:RecvDataString += "��Ŀ����=���ա�08��31�ա���";break;
		case (byte)0x34:RecvDataString += "��Ŀ����=ʱ�֡�14ʱ35�֡���";break;
		case (byte)0x35:RecvDataString += "��Ŀ����=���ڡ� ������ ����";break;
		case (byte)0x36:RecvDataString += "��Ŀ����=ʱ���롰14:35:26����";break;
		case (byte)0x37:RecvDataString += "��Ŀ����=ʣ�೵λ����λ:999���򡰳�λ��������";break;
		case (byte)0x38:RecvDataString += "��Ŀ����=�Զ������ݣ�";break;
		default:RecvDataString += "��Ŀ����=δ֪��";
		}
		if(RecvDataBuf[headIndex] != (byte)0x0e)
		{
			headIndex = Index_Inc(headIndex);
			switch(RecvDataBuf[headIndex]) {//��ʾ��ʽ
			case (byte)0x30:RecvDataString += "��ʾ��ʽ=��ҳ��ʾ��";break;
			case (byte)0x31:RecvDataString += "��ʾ��ʽ=������ʾ��";break;
			case (byte)0x32:RecvDataString += "��ʾ��ʽ=ѭ��������ʾ��";break;
			default:RecvDataString += "��ʾ��ʽ=δ֪��";
			}
			headIndex = Index_Inc(headIndex);
			RecvDataString += "��ʾʱ��=" + (char)RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			RecvDataString += (char)RecvDataBuf[headIndex] + ".";
			headIndex = Index_Inc(headIndex);
			RecvDataString += (char)RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			RecvDataString += (char)RecvDataBuf[headIndex] + "�룬";
			headIndex = Index_Inc(headIndex);
			switch(RecvDataBuf[headIndex]) {//��ɫ
			case (byte)0x30:RecvDataString += "��ɫ=��ɫ��";break;
			case (byte)0x31:RecvDataString += "��ɫ=��ɫ��";break;
			case (byte)0x32:RecvDataString += "��ɫ=��ɫ��";break;
			default:RecvDataString += "��ɫ=δ֪��";
			}
			headIndex = Index_Inc(headIndex);
			int tmpIndex = headIndex;
			int count = 0;
			while(tmpIndex != etxIndex) {
				count++;
				tmpIndex = Index_Inc(tmpIndex);
			}
			String str = "";
			if(count != 0) {
				byte[] tmpByte = new byte[count];
				for(int i = 0; i < tmpByte.length; i++) {
					tmpByte[i] = RecvDataBuf[headIndex];
					headIndex = Index_Inc(headIndex);
				}
				try {
					str = new String(tmpByte, "GB2312");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			RecvDataString += "�Զ����Ŀ����=" + str + "\r\n";
		} else {
			RecvDataString += "���Զ����Ŀ����" + "\r\n";
		}
	}
	
	/* ��ȡֽƱ��ӡ���ݡ�������ֵ���� */
	private void getCommandBack_423D(int headIndex, int etxIndex) {
		RecvDataString += "��ȡֽƱ��ӡ��������ִ�гɹ���";
		RecvDataString += "�к�=" + (char)RecvDataBuf[headIndex] + "��";
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//����Ŵ�
		case (byte)0x30:RecvDataString += "����Ŵ�=������С��";break;
		case (byte)0x31:RecvDataString += "����Ŵ�=����Ŵ�1����";break;
		case (byte)0x32:RecvDataString += "����Ŵ�=����Ŵ�2����";break;
		case (byte)0x33:RecvDataString += "����Ŵ�=����Ŵ�3����";break;
		case (byte)0x34:RecvDataString += "����Ŵ�=����Ŵ�4����";break;
		default:RecvDataString += "����Ŵ�=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//���뷽ʽ
		case (byte)0x30:RecvDataString += "���뷽ʽ=����룬";break;
		case (byte)0x31:RecvDataString += "���뷽ʽ=���У�";break;
		case (byte)0x32:RecvDataString += "���뷽ʽ=�Ҷ��룬";break;
		default:RecvDataString += "���뷽ʽ=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "��ӡ����=" + str + "\r\n";
	}
	
	/* ��ȡ��ӡ���ؼ��ʡ�������ֵ���� */
	private void getCommandBack_423F(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ��ӡ���ؼ�������ִ�гɹ���";
		RecvDataString += "��Ŀ���=" + (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex] + "��";
		headIndex = Index_Inc(headIndex);
		RecvDataString += "������=" + (char)RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		RecvDataString += (char)RecvDataBuf[headIndex] + "��";
		headIndex = Index_Inc(headIndex);
		RecvDataString += "����=" + (char)RecvDataBuf[headIndex] + "��";
		headIndex = Index_Inc(headIndex);
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "��ַǰ׺=" + str + "\r\n";
	}
	
	private void getCommandBack_43(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "��ȴ�ȷ������ִ�гɹ�\r\n";break;
		case (byte)0x31:RecvDataString += "�˻�δע������ִ�гɹ�\r\n";break;
		case (byte)0x32:RecvDataString += "�˻�δ��Ȩ����ִ�гɹ�\r\n";break;
		case (byte)0x33:RecvDataString += "�˻�����������ִ�гɹ�\r\n";break;
		case (byte)0x34:RecvDataString += "�˻��ѹ�������ִ�гɹ�\r\n";break;
		case (byte)0x35:RecvDataString += "�˻���������ִ�гɹ�\r\n";break;
		case (byte)0x36:RecvDataString += "�����볡����ִ�гɹ�\r\n";break;
		case (byte)0x37:RecvDataString += "��δ�볡����ִ�гɹ�\r\n";break;
		case (byte)0x3a:RecvDataString += "���볡ͣ������ִ�гɹ�\r\n";break;
		case (byte)0x3b:RecvDataString += "���볡ͣ�����˻�ʣ��ʹ������XXXXXX������ִ�гɹ�\r\n";break;
		case (byte)0x3c:RecvDataString += "���볡ͣ�����˻�ʣ����SXXXXX.xxԪ����ִ�гɹ�\r\n";break;
		case (byte)0x3d:RecvDataString += "ף��һ·ƽ������ִ�гɹ�\r\n";break;
		case (byte)0x3e:RecvDataString += "ף��һ·ƽ�����˻�ʣ��ʹ������XXXXXX������ִ�гɹ�\r\n";break;
		case (byte)0x3f:RecvDataString += "ף��һ·ƽ�����˻�ʣ����SXXXXX.xxԪ����ִ�гɹ�\r\n";break;
		case (byte)0x40:RecvDataString += "�ֹ���բ����ִ�гɹ�\r\n";break;
		case (byte)0x41:RecvDataString += "�ֹ���բ����ִ�гɹ�\r\n";break;
		case (byte)0x42:RecvDataString += "�뽻�ѣ�SXXXXX.xxԪ����ִ�гɹ�\r\n";break;
		case (byte)0x43:RecvDataString += "�Բ����˻��ѹ��ڣ��뽻�ѣ�SXXXXX.xxԪ����ִ�гɹ�\r\n";break;
		case (byte)0x44:RecvDataString += "�Բ����˻����㣬�뽻�ѣ�SXXXXX.xxԪ����ִ�гɹ�\r\n";break;
		case (byte)0x45:RecvDataString += "��λ��������ִ�гɹ�\r\n";break;
		case (byte)0x46:RecvDataString += "��λ��������ִ�гɹ�\r\n";break;
		case (byte)0x4a:RecvDataString += "�����ѳ�ʱ����ִ�гɹ�\r\n";break;
		case (byte)0x4b:RecvDataString += "��δ���ѣ����ȵ������շѴ���������ִ�гɹ�\r\n";break;
		case (byte)0x4c:RecvDataString += "�뵽����������������ִ�гɹ�\r\n";break;
		case (byte)0x4d:RecvDataString += "ͣ��XX��XXСʱXX�֣��뽻�ѣ�SXXXXX.xxԪ����ִ�гɹ�\r\n";break;
		case (byte)0x4e:RecvDataString += "����������������ִ�гɹ�\r\n";break;
		case (byte)0x4f:RecvDataString += "�Բ��𣬳��ƺ�ʶ�������ȡ���볡(ͬʱ����3#�̵���)����ִ�гɹ�\r\n";break;
		case (byte)0x50:RecvDataString += "���ĳ�λ��ռ�ã�������ʱ���볡����ִ�гɹ�\r\n";break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}
	
	private void getCommandBack_45(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "һ���Ƿ��Զ��л�����ִ�гɹ�\r\n";break;
		case (byte)0x31:RecvDataString += "�����Ƿ��Զ��л�����ִ�гɹ�\r\n";break;
		case (byte)0x32:RecvDataString += "�����Ƿ��Զ��л�����ִ�гɹ�\r\n";break;
		case (byte)0x33:RecvDataString += "�Ĳ��Ƿ��Զ��л�����ִ�гɹ�\r\n";break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}
	
	private void getCommandBack_46(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "���ư��Ѹ�λ\r\n";break;
		case (byte)0x31:RecvDataString += "��ʾ��ά������ִ�гɹ�\r\n";break;
		case (byte)0x32:RecvDataString += "���ƿ�������ִ�гɹ�\r\n";break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}

	private void getCommandBack_48(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x31:RecvDataString += "һ�㷢����ʱ��ʾ����ִ�гɹ�\r\n";break;
		case (byte)0x32:RecvDataString += "���㷢����ʱ��ʾ����ִ�гɹ�\r\n";break;
		case (byte)0x33:RecvDataString += "���㷢����ʱ��ʾ����ִ�гɹ�\r\n";break;
		case (byte)0x34:RecvDataString += "�Ĳ㷢����ʱ��ʾ����ִ�гɹ�\r\n";break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}

	private void getCommandBack_49(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:RecvDataString += "���ϼ̵�������ִ�гɹ�\r\n";break;
		case (byte)0x31:RecvDataString += "����ָ��ʱ����ͷż̵�������ִ�гɹ�\r\n";break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}

	private void getCommandBack_4B(int headIndex, int etxIndex) {
		if(RecvFc2 == (byte)0x30) {
			RecvDataString += "��ȡ���ư����к�����ִ�гɹ������к�=";
			for(int i = 0; i < 8; i++) {
				switch(RecvDataBuf[headIndex]) {
				case (byte)0x3a:RecvDataString += 'A';break;
				case (byte)0x3b:RecvDataString += 'B';break;
				case (byte)0x3c:RecvDataString += 'C';break;
				case (byte)0x3d:RecvDataString += 'D';break;
				case (byte)0x3e:RecvDataString += 'E';break;
				case (byte)0x3f:RecvDataString += 'F';break;
				default:RecvDataString += (char)RecvDataBuf[headIndex];
				}
				headIndex = Index_Inc(headIndex);
			}
			RecvDataString += "\r\n";
		} else {
			RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}
	
	private void getCommandBack_4F(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x50:RecvDataString += "��������/�ѻ�ģʽ����ִ�гɹ�\r\n";break;
		case (byte)0x51:getCommandBack_4F51(headIndex, etxIndex);break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}

	/* ��ȡ����/�ѻ�ģʽ��������ֵ���� */
	private void getCommandBack_4F51(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ����/�ѻ�ģʽ����ִ�гɹ���";
		switch(RecvDataBuf[headIndex]) {//�ϵ���Ƿ��ѻ�
		case (byte)0x30:RecvDataString += "�ϵ���Ƿ��ѻ�=�ϵ���ѻ���";break;
		case (byte)0x31:RecvDataString += "�ϵ���Ƿ��ѻ�=�ϵ�����ߣ�";break;
		default:RecvDataString += "�ϵ���Ƿ��ѻ�=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//��ǰ�Ƿ��ѻ�
		case (byte)0x30:RecvDataString += "��ǰ�Ƿ��ѻ�=��ǰ�ѻ���";break;
		case (byte)0x31:RecvDataString += "��ǰ�Ƿ��ѻ�=��ǰ���ߣ�";break;
		default:RecvDataString += "��ǰ�Ƿ��ѻ�=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		RecvDataString += "�����Զ�ת�ѻ�����=" + RecvDataBuf[headIndex] + "��";
		headIndex = Index_Inc(headIndex);
		byte tmp1 = RecvDataBuf[headIndex];
		headIndex = Index_Inc(headIndex);
		byte tmp2 = RecvDataBuf[headIndex];
		int tmp3 = ((tmp1 & 0x0f) << 4) | (tmp2 & 0x0f);
		double tmp4 = tmp3 / 100;
		RecvDataString += "�ϴ�������ʱ��=" + tmp4 + "��\r\n";
	}
	
	/* ��ȡ�汾�š�������ֵ���� */
	private void getCommandBack_50(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ�汾������ִ�гɹ���";
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "�汾��=" + str + "\r\n";
	}
	
	private void getCommandBack_61(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:getCommandBack_6130(headIndex, etxIndex);break;
		case (byte)0x31:getCommandBack_6131(headIndex, etxIndex);break;
		case (byte)0x32:getCommandBack_6132(headIndex, etxIndex);break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}
	/* ��⵽����״̬�仯��ÿ�α仯ֻ�ϴ�1�Σ���������ֵ���� */
	private void getCommandBack_6130(int headIndex, int etxIndex) {
		RecvDataString += "��⵽����״̬�仯��ÿ�α仯ֻ�ϴ�1�Σ���";
		switch(RecvDataBuf[headIndex]) {//״̬1
		case (byte)0x30:RecvDataString += "״̬1=������";break;
		case (byte)0x31:RecvDataString += "״̬1=���ư��⵽�տ�������һ�ţ�";break;
		case (byte)0x32:RecvDataString += "״̬1=���ư��⵽�������ο�һ�ţ�";break;
		default:RecvDataString += "״̬1=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		switch(RecvDataBuf[headIndex]) {//״̬2
		case (byte)0x30:RecvDataString += "״̬2=������";break;
		case (byte)0x31:RecvDataString += "״̬2=�������������㣬";break;
		case (byte)0x32:RecvDataString += "״̬2=���������գ�";break;
		default:RecvDataString += "״̬2=δ֪��";
		}
		RecvDataString += "\r\n";
	}
	/* WGA�ڶ���һ�ţ������������ϴ�������λ����Ӧ����ÿ��3���ϴ�һ�Σ���������ֵ���� */
	private void getCommandBack_6131(int headIndex, int etxIndex) {
		RecvDataString += "WGA�ڶ���һ�ţ������������ϴ�������λ����Ӧ����ÿ��3���ϴ�һ�Σ���";
		for(int i = 0; i < 8; i++) {
			byte tmp = RecvDataBuf[headIndex];
			switch(tmp) {
			case (byte)0x3a:RecvDataString += 'A';break;
			case (byte)0x3b:RecvDataString += 'B';break;
			case (byte)0x3c:RecvDataString += 'C';break;
			case (byte)0x3d:RecvDataString += 'D';break;
			case (byte)0x3e:RecvDataString += 'E';break;
			case (byte)0x3f:RecvDataString += 'F';break;
			default:RecvDataString += RecvDataBuf[headIndex];
			}
			headIndex = Index_Inc(headIndex);
		}
		RecvDataString += "\r\n";
	}

	/* WGB�ڶ���һ�ţ������������ϴ�������λ����Ӧ����ÿ��3���ϴ�һ�Σ���������ֵ���� */
	private void getCommandBack_6132(int headIndex, int etxIndex) {
		RecvDataString += "WGB�ڶ���һ�ţ������������ϴ�������λ����Ӧ����ÿ��3���ϴ�һ�Σ���";
		for(int i = 0; i < 8; i++) {
			byte tmp = RecvDataBuf[headIndex];
			switch(tmp) {
			case (byte)0x3a:RecvDataString += 'A';break;
			case (byte)0x3b:RecvDataString += 'B';break;
			case (byte)0x3c:RecvDataString += 'C';break;
			case (byte)0x3d:RecvDataString += 'D';break;
			case (byte)0x3e:RecvDataString += 'E';break;
			case (byte)0x3f:RecvDataString += 'F';break;
			default:RecvDataString += RecvDataBuf[headIndex];
			}
			headIndex = Index_Inc(headIndex);
		}
		RecvDataString += "\r\n";
	}
	
	private void getCommandBack_82(int headIndex, int etxIndex) {
		RecvDataString += "�ϴ���ά�����ݣ�";
		switch(RecvDataBuf[headIndex]) {//��������
		case (byte)0x30:RecvDataString += "��������=��ӡ����ӡ�Ķ�ά�룬";break;
		case (byte)0x31:RecvDataString += "��������=ɨ��ǹɨ��Ķ�ά�룬";break;
		default:RecvDataString += "��������=δ֪��";
		}
		headIndex = Index_Inc(headIndex);
		int tmpIndex = headIndex;
		int count = 0;
		while(tmpIndex != etxIndex) {
			count++;
			tmpIndex = Index_Inc(tmpIndex);
		}
		String str = "";
		if(count != 0) {
			byte[] tmpByte = new byte[count];
			for(int i = 0; i < tmpByte.length; i++) {
				tmpByte[i] = RecvDataBuf[headIndex];
				headIndex = Index_Inc(headIndex);
			}
			try {
				str = new String(tmpByte, "GB2312");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		RecvDataString += "��ά������=" + str + "\r\n";
	}

	private void getCommandBack_83(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:getCommandBack_8330(headIndex, etxIndex);break;
		case (byte)0x31:getCommandBack_8331(headIndex, etxIndex);break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}

	private void getCommandBack_84(int headIndex, int etxIndex) {
		switch(RecvFc2) {
		case (byte)0x30:
			RecvDataString += "�յ�һ��������ݣ���ʾ�ж�ʱ��=";
			byte tmp1,tmp2,tmp3,tmp4;
			int tmpa,tmpb,tmpc,tmpd;
			tmp1 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp2 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp3 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp4 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmpa = ((tmp1 & 0x0f) << 4) | (tmp2 & 0x0f);
			tmpb = ((tmp3 & 0x0f) << 4) | (tmp4 & 0x0f);
			tmpc = (tmpa << 8) | tmpb;
			tmp1 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp2 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp3 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmp4 = RecvDataBuf[headIndex];
			headIndex = Index_Inc(headIndex);
			tmpa = ((tmp1 & 0x0f) << 4) | (tmp2 & 0x0f);
			tmpb = ((tmp3 & 0x0f) << 4) | (tmp4 & 0x0f);
			tmpd = (tmpa << 8) | tmpb;
			RecvDataString += tmpc + ", " + tmpd + ", sub=" + (tmpd- tmpc) + "\r\n";
			break;
		default:RecvDataString += "�յ�һ��δ֪���ܵ�����\r\n";
		}
	}

	/* ��ȡ/�ϴ����ư�״̬��������ֵ���� */
	private void getCommandBack_8330(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ/�ϴ����ư�״̬����ִ�гɹ���";
		byte tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x01) {//bit0
			RecvDataString += "��ӡ����ֽ����";
		} 
		if((byte)(tmp1 & 0x02) == (byte)0x02) {//bit1
			RecvDataString += "��ӡ��ֽ���٣�";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x04) {//bit2
			RecvDataString += "��ӡ����е���ϣ�";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x08) {//bit3
			RecvDataString += "��ӡ����ť�����£�";
		} 
		headIndex = Index_Inc(headIndex);
		tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x01) {//bit0
			RecvDataString += "��ӡ���е�����";
		} 
		if((byte)(tmp1 & 0x02) == (byte)0x02) {//bit1
			RecvDataString += "��ӡ������";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x04) {//bit2
			RecvDataString += "��ӡͷ�¶ȹ��ߣ�";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x08) {//bit3
			RecvDataString += "��ӡ��ʧȥ���ӣ�";
		} 
		headIndex = Index_Inc(headIndex);
		tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x01) {//bit0
			RecvDataString += "��ֽ�������";
		} 
		if((byte)(tmp1 & 0x02) == (byte)0x02) {//bit1
			RecvDataString += "��ʱδȡƱ��";
		} 
		RecvDataString += "\r\n";
	}

	/* ��ȡ���ư��������״̬��������ֵ���� */
	private void getCommandBack_8331(int headIndex, int etxIndex) {
		RecvDataString += "��ȡ���ư��������״̬����ִ�гɹ���";
		byte tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x00) {//bit0
			RecvDataString += "IN1�����룬";
		}
		if((byte)(tmp1 & 0x02) == (byte)0x00) {//bit1
			RecvDataString += "IN2�����룬";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x00) {//bit2
			RecvDataString += "IN3�����룬";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x00) {//bit3
			RecvDataString += "IN4�����룬";
		} 
		headIndex = Index_Inc(headIndex);
		tmp1 = RecvDataBuf[headIndex];
		if((byte)(tmp1 & 0x01) == (byte)0x00) {//bit0
			RecvDataString += "OUT1�̵������ϣ�";
		}
		if((byte)(tmp1 & 0x02) == (byte)0x00) {//bit1
			RecvDataString += "OUT2�̵������ϣ�";
		} 
		if((byte)(tmp1 & 0x04) == (byte)0x00) {//bit2
			RecvDataString += "OUT3�̵������ϣ�";
		} 
		if((byte)(tmp1 & 0x08) == (byte)0x00) {//bit3
			RecvDataString += "OUT4�̵������ϣ�";
		} 
		RecvDataString += "\r\n";
	}
}
