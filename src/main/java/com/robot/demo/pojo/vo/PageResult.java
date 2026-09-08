package com.robot.demo.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private Long total;
    private Long pageNum;
    private Long pageSize;
    private List<T> records;
    public static <T>  PageResult<T> of(Long total, Long pageNum, Long pageSize, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setRecords(records);
        return result;
    }
}
