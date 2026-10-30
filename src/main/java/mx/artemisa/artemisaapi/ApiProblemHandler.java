package mx.artemisa.artemisaapi;

import jakarta.servlet.http.HttpServletRequest;
import mx.artemisa.artemisaapi.identity.TenantOnboardingConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiProblemHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex,HttpServletRequest req) {
        ProblemDetail p=problem(HttpStatus.BAD_REQUEST,"La solicitud contiene campos inválidos.",req);
        p.setProperty("errors",ex.getBindingResult().getFieldErrors().stream()
            .map(e->Map.of("field",e.getField(),"message",e.getDefaultMessage()==null?"Valor inválido":e.getDefaultMessage()))
            .toList()); return p;
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex,HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST,"El cuerpo de la solicitud no es JSON válido o contiene valores con formato incorrecto.",req);
    }
    @ExceptionHandler(TenantOnboardingConflictException.class)
    ProblemDetail onboardingConflict(Exception ex,HttpServletRequest req) { return problem(HttpStatus.CONFLICT,safeMessage(ex,"La institución o el correo ya existen."),req); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(HttpServletRequest req) { return problem(HttpStatus.CONFLICT,"La operación entra en conflicto con datos existentes.",req); }
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(Exception ex,HttpServletRequest req) { return problem(HttpStatus.FORBIDDEN,"No tienes permiso para realizar esta operación.",req); }
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException ex,HttpServletRequest req) {
        return problem(ex.getStatusCode(),ex.getReason()==null?"La solicitud no pudo completarse.":ex.getReason(),req);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex,HttpServletRequest req) { return problem(HttpStatus.BAD_REQUEST,safeMessage(ex,"Parámetros inválidos."),req); }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex,HttpServletRequest req) { return problem(HttpStatus.INTERNAL_SERVER_ERROR,"Ocurrió un error interno. Intenta más tarde.",req); }

    private ProblemDetail problem(org.springframework.http.HttpStatusCode status,String detail,HttpServletRequest req) {
        ProblemDetail p=ProblemDetail.forStatusAndDetail(status,detail);
        p.setType(URI.create("about:blank")); p.setTitle(title(status));
        p.setInstance(URI.create(req.getRequestURI()));
        p.setProperty("timestamp",OffsetDateTime.now().toString());
        p.setProperty("code",status.value()); return p;
    }
    private String title(org.springframework.http.HttpStatusCode status) {
        HttpStatus resolved=HttpStatus.resolve(status.value()); return resolved==null?"Error":resolved.getReasonPhrase();
    }
    private String safeMessage(Exception e,String fallback) { return e.getMessage()==null||e.getMessage().isBlank()?fallback:e.getMessage(); }
}
