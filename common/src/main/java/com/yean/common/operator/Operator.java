package com.yean.common.operator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Operator {

    private Long userId;
    private String username;
    private String role;
}
