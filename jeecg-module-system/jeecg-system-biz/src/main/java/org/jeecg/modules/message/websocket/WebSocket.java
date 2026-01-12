package org.jeecg.modules.message.websocket;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.base.BaseMap;
import org.jeecg.common.constant.WebsocketConst;
import org.jeecg.common.modules.redis.client.JeecgRedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author scott
 * @Date 2019/11/29 9:41
 * @Description: 此注解相当于设置访问URL
 */
@Component
@Slf4j
@ServerEndpoint("/websocket/{userId}")
public class WebSocket {


    private static final ConcurrentHashMap<String, Session> SESSION_POOL = new ConcurrentHashMap<>();

    public static final String REDIS_TOPIC_NAME = "socketHandler";

    //避免初次调用出现空指针的情况
    private static JeecgRedisClient jeecgRedisClient;
    @Autowired
    private void setJeecgRedisClient(JeecgRedisClient jeecgRedisClient){
        WebSocket.jeecgRedisClient = jeecgRedisClient;
    }

    //==========【websocket接受、推送消息等方法 —— 具体服务节点推送ws消息】========================================================================================
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "userId") String userId) {
        SESSION_POOL.put(userId, session);
        log.debug("【系统 WebSocket】有新的连接，总数为:" + SESSION_POOL.size());
    }

    @OnClose
    @SuppressWarnings("resource")
    public void onClose(@PathParam("userId") String userId) {
        SESSION_POOL.remove(userId);
        log.debug("【系统 WebSocket】连接断开，总数为:" + SESSION_POOL.size());
    }

    /**
     * ws推送消息
     */
    public void pushMessage(String userId, String message) {
        SESSION_POOL.entrySet().stream()
                .filter(entry -> entry.getKey().contains(userId) && entry.getValue().isOpen())
                .forEach(entry -> {
                    Session session = entry.getValue();
                    try {
                        synchronized (session) {
                            session.getBasicRemote().sendText(message);
                        }
                        log.debug("【系统 WebSocket】推送消息成功: {}", message);
                    } catch (Exception e) {
                        log.error("消息发送失败: {}", e.getMessage(), e);
                    }
                });
    }


    /**
     * ws遍历群发消息
     */
    public void pushMessage(String message) {
        SESSION_POOL.values().forEach(session -> session.getAsyncRemote().sendText(message));
        log.debug("【系统 WebSocket】群发消息: {}", message);
    }

    /**
     * 封装消息json
     */
    public static String packageMessage(String msgId, String msgText) {
        JSONObject message = new JSONObject();
        message.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_TOPIC);
        message.put(WebsocketConst.MSG_ID, msgId);
        message.put(WebsocketConst.MSG_TXT, msgText);
        return message.toJSONString();
    }

    /**
     * ws接受客户端消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("userId") String userId) {
        if ("ping".equals(message) || WebsocketConst.CMD_CHECK.equals(message)) {
            log.debug("【系统 WebSocket】收到来自[{}]心跳消息: {}, ", userId, message);
            // 回应心跳消息不然会自动断开
            pushMessage(userId, "pong");
        } else {
            log.debug("【系统 WebSocket】收到客户端消息: {}", message);
        }

    }

    /**
     * 配置错误信息处理
     */
    @OnError
    public void onError(Session session, Throwable t) {
        log.warn("【系统 WebSocket】发生错误: ", t);
    }
    //==========【系统 WebSocket接受、推送消息等方法 —— 具体服务节点推送ws消息】========================================================================================


    //==========【采用redis发布订阅模式——推送消息】========================================================================================
    /**
     * 后台发送消息到redis
     */
    public void sendMessage(String message) {
        BaseMap baseMap = new BaseMap();
        baseMap.put("userId", "");
        baseMap.put("message", message);
        jeecgRedisClient.sendMessage(WebSocket.REDIS_TOPIC_NAME, baseMap);
    }

    /**
     * 此为单点消息 redis
     */
    public void sendMessage(String userId, String message) {
        BaseMap baseMap = new BaseMap();
        baseMap.put("userId", userId);
        baseMap.put("message", message);
        jeecgRedisClient.sendMessage(WebSocket.REDIS_TOPIC_NAME, baseMap);
    }

    /**
     * 此为单点消息(多人) redis
     */
    public void sendMessage(String[] userIds, String message) {
        Arrays.stream(userIds).forEach(userId -> sendMessage(userId, message));
    }
    //=======【采用redis发布订阅模式——推送消息】==========================================================================================

}