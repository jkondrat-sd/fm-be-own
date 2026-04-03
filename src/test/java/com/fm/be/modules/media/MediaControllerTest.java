package com.fm.be.modules.media;

import com.fm.be.modules.media.dto.MediaConfirmDto;
import com.fm.be.modules.media.dto.MediaConfirmResponseDto;
import com.fm.be.modules.media.dto.PresignedUrlRequestDto;
import com.fm.be.modules.media.dto.PresignedUrlResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaController mediaController;

    @Nested
    class GetPresignedUrl {

        @Test
        @DisplayName("delegates to mediaService.generatePresignedUrl and returns PresignedUrlResponseDto")
        void delegatesToService() {
            PresignedUrlRequestDto dto = mock(PresignedUrlRequestDto.class);
            PresignedUrlResponseDto response = mock(PresignedUrlResponseDto.class);
            when(mediaService.generatePresignedUrl(dto)).thenReturn(response);

            PresignedUrlResponseDto result = mediaController.getPresignedUrl(dto);

            assertThat(result).isSameAs(response);
            verify(mediaService).generatePresignedUrl(dto);
        }
    }

    @Nested
    class ConfirmUpload {

        @Test
        @DisplayName("delegates to mediaService.confirmUpload and returns MediaConfirmResponseDto")
        void delegatesToService() {
            MediaConfirmDto dto = mock(MediaConfirmDto.class);
            MediaConfirmResponseDto response = mock(MediaConfirmResponseDto.class);
            when(mediaService.confirmUpload(dto)).thenReturn(response);

            MediaConfirmResponseDto result = mediaController.confirmUpload(dto);

            assertThat(result).isSameAs(response);
            verify(mediaService).confirmUpload(dto);
        }
    }
}
