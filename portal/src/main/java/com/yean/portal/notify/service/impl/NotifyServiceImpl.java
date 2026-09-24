package com.yean.portal.notify.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.result.PageResult;
import com.yean.portal.notify.dto.NotifyQueryDTO;
import com.yean.portal.notify.dto.NotifySendDTO;
import com.yean.portal.notify.dto.NotifyVO;
import com.yean.portal.notify.entity.Notify;
import com.yean.portal.notify.enums.NotifyChannel;
import com.yean.portal.notify.mapper.NotifyMapper;
import com.yean.portal.notify.service.NotifyService;
import com.yean.portal.user.entity.User;
import com.yean.portal.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl implements NotifyService {

    private final NotifyMapper notifyMapper;
    private final UserMapper userMapper;

    @Override
    public Long send(NotifySendDTO dto) {
        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BizException(ErrorEnum.USER_NOT_FOUND);
        }
        NotifyChannel channel = parseChannel(dto.getChannel());
        Notify row = new Notify();
        row.setUserId(dto.getUserId());
        row.setTitle(dto.getTitle());
        row.setContent(dto.getContent());
        row.setChannel(channel.name());
        row.setBizType(StringUtils.hasText(dto.getBizType()) ? dto.getBizType().trim().toUpperCase(Locale.ROOT) : "SYSTEM");
        row.setReadFlag(0);
        notifyMapper.insert(row);
        if (channel == NotifyChannel.SMS) {
            log.info("sms skipped, notifyId={}, userId={}", row.getId(), row.getUserId());
        } else if (channel == NotifyChannel.MAIL) {
            log.info("mail skipped, notifyId={}, userId={}", row.getId(), row.getUserId());
        }
        return row.getId();
    }

    @Override
    public PageResult<NotifyVO> list(NotifyQueryDTO query) {
        NotifyQueryDTO cond = query == null ? new NotifyQueryDTO() : query;
        IPage<Notify> result = notifyMapper.selectNotifyList(
                new Page<>(cond.pageOrDefault(), cond.sizeOrDefault()), cond);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
    }

    @Override
    public void markRead(Long id) {
        Notify row = notifyMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorEnum.NOTICE_NOT_FOUND);
        }
        row.setReadFlag(1);
        notifyMapper.updateById(row);
    }

    private NotifyChannel parseChannel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return NotifyChannel.INBOX;
        }
        try {
            return NotifyChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "非法通知渠道: " + raw);
        }
    }

    private NotifyVO toVO(Notify row) {
        NotifyVO vo = new NotifyVO();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setChannel(row.getChannel());
        vo.setBizType(row.getBizType());
        vo.setReadFlag(row.getReadFlag());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
