/*
 * MainFrame.java
 *
 * Created on 2016.8.19
 */

package com.yang.serialport.ui;

import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.yang.serialport.exception.NoSuchPort;
import com.yang.serialport.exception.NotASerialPort;
import com.yang.serialport.exception.PortInUse;
import com.yang.serialport.exception.SendDataToSerialPortFailure;
import com.yang.serialport.exception.SerialPortOutputStreamCloseFailure;
import com.yang.serialport.exception.SerialPortParameterFailure;
import com.yang.serialport.exception.TooManyListeners;
import com.yang.serialport.manage.SerialPortManager;
import com.yang.serialport.utils.ByteUtils;
import com.yang.serialport.utils.ShowUtils;

/**
 * 实验4.2 串口编程指导 - 主界面
 * 功能：
 *  1. 选择串口与波特率
 *  2. 打开/关闭串口
 *  3. 实时显示接收到的数据
 *  4. 手动发送下行命令
 */
public class MainFrame extends JFrame {

    private JComboBox<String> portListCombo;
    private JComboBox<String> baudRateCombo;
    private JButton openButton;
    private JButton closeButton;
    private JButton sendButton;
    private JButton clearButton;
    private JTextArea receiveArea;
    private JTextField sendField;

    private SerialPortManager serialPortManager;

    public MainFrame() {
        setTitle("WSN 实验4.2 串口调试程序");
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        serialPortManager = new SerialPortManager();

        // ====== 上方控制区 ======
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        // 串口选择
        controlPanel.add(new JLabel("串口号:"));
        portListCombo = new JComboBox<>();
        listAvailablePorts();
        controlPanel.add(portListCombo);

        // 波特率选择
        controlPanel.add(new JLabel("波特率:"));
        baudRateCombo = new JComboBox<>(new String[]{"9600", "115200"});
        baudRateCombo.setSelectedItem("115200");
        controlPanel.add(baudRateCombo);

        // 打开/关闭按钮
        openButton = new JButton("打开串口");
        closeButton = new JButton("关闭串口");
        controlPanel.add(openButton);
        controlPanel.add(closeButton);

        add(controlPanel, BorderLayout.NORTH);

        // ====== 中间数据显示区 ======
        receiveArea = new JTextArea();
        receiveArea.setEditable(false);
        receiveArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(receiveArea);
        add(scrollPane, BorderLayout.CENTER);

        // ====== 下方发送区 ======
        JPanel sendPanel = new JPanel();
        sendPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        sendPanel.add(new JLabel("发送指令(HEX):"));
        sendField = new JTextField(40);
        sendPanel.add(sendField);
        sendButton = new JButton("发送");
        clearButton = new JButton("清空显示");
        sendPanel.add(sendButton);
        sendPanel.add(clearButton);
        add(sendPanel, BorderLayout.SOUTH);

        // 事件绑定
        bindEvents();

        // 把 ShowUtils 的输出绑定到文本框
        ShowUtils.setOutputArea(receiveArea);
    }

    /**
     * 扫描可用串口并填入下拉框
     */
    private void listAvailablePorts() {
        Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                portListCombo.addItem(portId.getName());
            }
        }
        if (portListCombo.getItemCount() == 0) {
            portListCombo.addItem("无可用端口");
        }
    }

    /**
     * 事件监听器绑定
     */
    private void bindEvents() {

        // 打开串口
        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String portName = (String) portListCombo.getSelectedItem();
                int baudRate = Integer.parseInt((String) baudRateCombo.getSelectedItem());
                try {
                    serialPortManager.openPort(portName, baudRate);
                } catch (Exception ex) {
                    ShowUtils.showData("❌ 打开串口失败: " + ex.getMessage());
                }
            }
        });

        // 关闭串口
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                serialPortManager.closePort();
            }
        });

        // 发送指令
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!serialPortManager.isOpened()) {
                    ShowUtils.showData("⚠️ 串口未打开！");
                    return;
                }
                String hexStr = sendField.getText().trim().replace(" ", "");
                if (hexStr.isEmpty()) {
                    ShowUtils.showData("⚠️ 请输入要发送的16进制指令。");
                    return;
                }
                try {
                    byte[] data = ByteUtils.hexStringToBytes(hexStr);
                    serialPortManager.sendToPort(data);
                } catch (Exception ex) {
                    ShowUtils.showData("❌ 指令格式错误或发送失败: " + ex.getMessage());
                }
            }
        });

        // 清空显示
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                receiveArea.setText("");
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}