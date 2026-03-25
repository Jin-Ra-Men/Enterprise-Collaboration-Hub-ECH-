package com.ech.backend.domain.channel;

public enum ChannelType {
    PUBLIC,
    PRIVATE,
    /** 1:1/소그룹 DM(내부 이름은 `__dm__…` 등으로 구분) */
    DM
}
