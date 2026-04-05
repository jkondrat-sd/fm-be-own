package com.fm.be.modules.sites;

import com.fm.be.modules.sites.dto.CreateSiteDto;
import com.fm.be.modules.sites.entity.Site;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SiteIntegrationTest {

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private SiteRepository siteRepository;

    @Autowired
    private SiteService siteService;

    @Test
    void testCreateSite() {
        when(siteRepository.save(any(Site.class))).thenAnswer(i -> {
            Site s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateSiteDto dto = CreateSiteDto.builder()
                .name("Mumbai HQ")
                .address("123 Marine Drive, Mumbai")
                .latitude(19.076)
                .longitude(72.8777)
                .geofenceRadius(500.0)
                .organizationId(UUID.fromString("00000000-0000-4000-a000-000000000001"))
                .build();
        siteService.createSite(dto);
    }
}
