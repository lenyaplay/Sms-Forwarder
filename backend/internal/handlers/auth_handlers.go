package handlers

import (
	"encoding/json"
	"errors"
	"net/http"

	"sms_forwarder/backend/internal/services"
)

type authCredentialsRequest struct {
	Login    string `json:"login"`
	Password string `json:"password"`
}

type refreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type tokenPairResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
}

func registerHandler(svc *services.AuthService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req authCredentialsRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid request body")
			return
		}
		if req.Login == "" || len(req.Password) < 8 {
			writeError(w, http.StatusBadRequest, "login is required and password must be at least 8 characters")
			return
		}

		pair, err := svc.Register(r.Context(), req.Login, req.Password)
		switch {
		case errors.Is(err, services.ErrLoginTaken):
			writeError(w, http.StatusConflict, "login already taken")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusCreated, tokenPairResponse{AccessToken: pair.AccessToken, RefreshToken: pair.RefreshToken})
		}
	}
}

func loginHandler(svc *services.AuthService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req authCredentialsRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid request body")
			return
		}
		if req.Login == "" || req.Password == "" {
			writeError(w, http.StatusBadRequest, "login and password are required")
			return
		}

		pair, err := svc.Login(r.Context(), req.Login, req.Password)
		switch {
		case errors.Is(err, services.ErrInvalidCredentials):
			writeError(w, http.StatusUnauthorized, "invalid login or password")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusOK, tokenPairResponse{AccessToken: pair.AccessToken, RefreshToken: pair.RefreshToken})
		}
	}
}

func refreshHandler(svc *services.AuthService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req refreshRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.RefreshToken == "" {
			writeError(w, http.StatusBadRequest, "refresh_token is required")
			return
		}

		pair, err := svc.Refresh(r.Context(), req.RefreshToken)
		switch {
		case errors.Is(err, services.ErrInvalidRefreshToken):
			writeError(w, http.StatusUnauthorized, "invalid refresh token")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusOK, tokenPairResponse{AccessToken: pair.AccessToken, RefreshToken: pair.RefreshToken})
		}
	}
}

func logoutHandler(svc *services.AuthService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req refreshRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.RefreshToken == "" {
			writeError(w, http.StatusBadRequest, "refresh_token is required")
			return
		}

		if err := svc.Logout(r.Context(), req.RefreshToken); err != nil {
			writeError(w, http.StatusInternalServerError, "internal error")
			return
		}

		writeJSON(w, http.StatusOK, map[string]string{})
	}
}
