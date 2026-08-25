package com.ams.hrms.dto;

/** Generic label/count pair used by bar and pie charts. */
public record CategoryCount(String label, long count) {
}
