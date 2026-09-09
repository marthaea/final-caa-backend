package ug.go.caa.recruitment.feature.recruitment.web;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ug.go.caa.recruitment.feature.recruitment.application.CvUploadService;
import ug.go.caa.recruitment.feature.recruitment.application.CvUploadService.FileResponse;
import ug.go.caa.recruitment.shared.integration.LocalUploadService;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/cv")
public class CvUploadController {

    private final CvUploadService uploads;

    public CvUploadController(CvUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<FileResponse> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String type
    ) {
        return ApiResponse.success(uploads.upload(AuthenticatedActor.from(jwt), file, type));
    }

    @GetMapping("/files/{filename}")
    ResponseEntity<Resource> localFile(@PathVariable String filename) {
        Resource resource = uploads.loadLocalFile(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .contentType(MediaType.parseMediaType(LocalUploadService.contentTypeFor(filename)))
                .body(resource);
    }
}
