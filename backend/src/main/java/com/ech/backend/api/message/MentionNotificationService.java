package com.ech.backend.api.message;

import com.ech.backend.common.mention.MentionParser;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.user.User;
import com.ech.backend.integration.realtime.RealtimeBroadcastClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 멘션 대상에게 Node 실시간 서버로 {@code mention:notify}를 보낸다.
 * 본문 형식: {@code @{사원번호|표시명}} (프론트 자동완성).
 */
@Service
public class MentionNotificationService {

    private final ChannelMemberRepository channelMemberRepository;
    private final RealtimeBroadcastClient realtimeBroadcastClient;

    public MentionNotificationService(
            ChannelMemberRepository channelMemberRepository, RealtimeBroadcastClient realtimeBroadcastClient) {
        this.channelMemberRepository = channelMemberRepository;
        this.realtimeBroadcastClient = realtimeBroadcastClient;
    }

    public void dispatchForNewMessage(Channel channel, Message message, User sender) {
        if (channel == null || message == null || sender == null) {
            return;
        }
        String body = message.getBody();
        if (body == null || body.isBlank()) {
            return;
        }
        Set<String> mentioned = MentionParser.collectEmployeeNos(body);
        mentioned.remove(sender.getEmployeeNo());
        if (mentioned.isEmpty()) {
            return;
        }
        List<String> valid = channelMemberRepository.findMemberEmployeeNosInChannel(
                channel.getId(), mentioned);
        if (valid.isEmpty()) {
            return;
        }
        String channelName = channelDisplayName(channel);
        String channelType = channel.getChannelType() != null ? channel.getChannelType().name() : "PUBLIC";
        String senderName = sender.getName() != null && !sender.getName().isBlank()
                ? sender.getName()
                : sender.getEmployeeNo();
        String preview = MentionParser.previewForToast(body, 120);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String emp : valid) {
            Map<String, Object> row = new HashMap<>();
            row.put("targetEmployeeNo", emp);
            row.put("channelId", channel.getId());
            row.put("channelName", channelName);
            row.put("channelType", channelType);
            row.put("senderName", senderName);
            row.put("messagePreview", preview);
            row.put("messageId", message.getId());
            items.add(row);
        }
        realtimeBroadcastClient.notifyMentions(items);
    }

    private static String channelDisplayName(Channel c) {
        if (c.getChannelType() == ChannelType.DM
                && c.getDescription() != null
                && !c.getDescription().isBlank()) {
            return c.getDescription();
        }
        return c.getName() != null ? c.getName() : "채널";
    }
}
