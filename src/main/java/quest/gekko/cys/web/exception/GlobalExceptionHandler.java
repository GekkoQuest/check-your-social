package quest.gekko.cys.web.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        log.warn("Response status exception: {} for URL: {}", ex.getMessage(), request.getRequestURL());

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", ex.getReason());
        mav.addObject("status", ex.getStatusCode().value());
        return mav;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request: {} for URL: {}", ex.getMessage(), request.getRequestURL());

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", "Invalid request: " + ex.getMessage());
        mav.addObject("status", 400);
        return mav;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error for URL: {}", request.getRequestURL(), ex);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", "An unexpected error occurred");
        mav.addObject("status", 500);
        return mav;
    }
}