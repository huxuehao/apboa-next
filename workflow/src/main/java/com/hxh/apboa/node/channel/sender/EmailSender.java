package com.hxh.apboa.node.channel.sender;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.channel.entity.Channel;
import com.hxh.apboa.common.util.FuncUtils;

import com.hxh.apboa.common.util.JsonUtils;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * 邮件发送器，基于 SMTP 协议发送
 *
 * @author huxuehao
 */
public class EmailSender implements ChannelSender {

    @Override
    public void send(Channel channel, MessageParams params) throws Exception {
        String configJson = channel.getConfig();
        if (FuncUtils.isEmpty(configJson)) {
            throw new RuntimeException("邮箱配置不能为空");
        }
        JsonNode config = JsonUtils.parse(configJson);

        String host = getString(config, "serverHost", true);
        int port = config.has("serverPort") ? config.get("serverPort").asInt(25) : 25;
        String sender = getString(config, "sender", true);
        String user = getString(config, "user", true);
        String password = getString(config, "passwd", true);
        boolean auth = "true".equalsIgnoreCase(getString(config, "enableSmtpAuth", false));
        boolean starttls = "true".equalsIgnoreCase(getString(config, "starttlsEnable", false));
        boolean ssl = "true".equalsIgnoreCase(getString(config, "sslEnable", false));
        String sslTrust = getString(config, "smtpSslTrust", false);

        String toRecipients = params.getExtraString("toRecipients");
        String ccRecipients = params.getExtraString("ccRecipients");
        String subject = params.getSubject();
        String content = params.getContent();

        if (FuncUtils.isEmpty(toRecipients)) {
            throw new RuntimeException("收件人不能为空");
        }

        Properties props = new Properties();
        props.setProperty("mail.smtp.host", host);
        props.setProperty("mail.smtp.port", String.valueOf(port));
        props.setProperty("mail.smtp.auth", String.valueOf(auth));
        props.setProperty("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.setProperty("mail.smtp.ssl.enable", String.valueOf(ssl));
        if (sslTrust != null) {
            props.setProperty("mail.smtp.ssl.trust", sslTrust);
        }
        props.setProperty("mail.transport.protocol", "smtp");

        Session session;
        if (auth) {
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password);
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(sender));
        for (String to : toRecipients.split("[,;]")) {
            String trimmed = to.trim();
            if (!trimmed.isEmpty()) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(trimmed));
            }
        }
        if (!FuncUtils.isEmpty(ccRecipients)) {
            for (String cc : ccRecipients.split("[,;]")) {
                String trimmed = cc.trim();
                if (!trimmed.isEmpty()) {
                    message.addRecipient(Message.RecipientType.CC, new InternetAddress(trimmed));
                }
            }
        }
        message.setSubject(subject != null ? subject : "");
        message.setContent(content, "text/html;charset=UTF-8");

        Transport.send(message);
    }

    private String getString(JsonNode node, String key, boolean required) {
        if (!node.has(key) || node.get(key).isNull()) {
            if (required) throw new RuntimeException("邮箱配置缺少必要字段: " + key);
            return null;
        }
        return node.get(key).asText();
    }
}
