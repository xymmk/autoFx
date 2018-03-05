package gmocoin.autoFX.windows;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import gmocoin.autoFX.Collabo.ISession;
import gmocoin.autoFX.Collabo.abs.AbsSession;

public class inputVerCdWin extends JFrame{
	ISession session;
	private JTextField veriCdField;
	private JFrame loginFrame;
	private JButton okBtn = new JButton("确定");
	public inputVerCdWin(ISession session,JFrame loginFrame){
		super.setTitle("请输入验证码");
		this.setContentPane(new JPanel());
		this.session = session;
		this.loginFrame = loginFrame;
		veriCdField = new JTextField(15);
		this.getContentPane().add(veriCdField);
		this.getContentPane().add(okBtn);
		JFrame self = this;
		okBtn.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				session.setVeriCd(veriCdField.getText());
	        	if(session.getLoginState() == AbsSession.USER_INVALID){
		        	JOptionPane.showMessageDialog(null, "验证码错误！");
	        	}else{
	        		self.setVisible(false);
	        		loginFrame.setVisible(false);
	        		new StatusWin(session,loginFrame);
	        	}
			}
		});
		this.setSize(240, 140);
	}
}
