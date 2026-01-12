package org.jeecg.common.exception;

/**
 * @Description: jeecg-boot自定义404异常
 * @author: jeecg-boot
 */
public class JeecgBoot404Exception extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public JeecgBoot404Exception(String message){
		super(message);
	}

	public JeecgBoot404Exception(Throwable cause)
	{
		super(cause);
	}

	public JeecgBoot404Exception(String message, Throwable cause)
	{
		super(message,cause);
	}
}
