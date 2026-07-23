package com.hxh.apboa.agent.service.impl;

import com.hxh.apboa.common.vo.CostRunItemVO;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CostStatServiceImplTest {

    @Test
    void shouldTreatMessageLessUsageAsInternalRun() {
        assertThat(CostStatServiceImpl.classifyPathStatus(null, Set.of(63, 64, 68)))
                .isEqualTo(CostRunItemVO.PATH_INTERNAL);
    }

    @Test
    void shouldTreatMessageOnCurrentPathAsCurrentReply() {
        assertThat(CostStatServiceImpl.classifyPathStatus(68, Set.of(63, 64, 68)))
                .isEqualTo(CostRunItemVO.PATH_CURRENT);
    }

    @Test
    void shouldOnlyTreatMessageOutsideCurrentPathAsDiscarded() {
        assertThat(CostStatServiceImpl.classifyPathStatus(72, Set.of(63, 64, 68)))
                .isEqualTo(CostRunItemVO.PATH_DISCARDED);
    }
}
