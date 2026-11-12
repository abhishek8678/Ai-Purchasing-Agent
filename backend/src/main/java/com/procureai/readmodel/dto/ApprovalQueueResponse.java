package com.procureai.readmodel.dto;

import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.approval.dto.ApprovalResponse;

public record ApprovalQueueResponse(AgentRunResponse run, ApprovalResponse approval) { }
