package auth_handler

type RegisterRequest struct {
	Username        string `json:"username"`
	Email           string `json:"email"`
	Password        string `json:"password"`
	GroupName       string `json:"studentGroup"`
	LegacyGroupName string `json:"group_name"`
}

type RegisterResponse struct {
	UserID  string `json:"userId"`
	Message string `json:"message"`
}

type LoginRequest struct {
	Login    string `json:"login"`
	Password string `json:"password"`
}

type TokenResponse struct {
	UserID       string `json:"userId"`
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
	TokenType    string `json:"tokenType"`
}

type RefreshRequest struct {
	RefreshToken       string `json:"refreshToken"`
	LegacyRefreshToken string `json:"refresh_token"`
}

type LogoutRequest struct {
	RefreshToken       string `json:"refreshToken"`
	LegacyRefreshToken string `json:"refresh_token"`
}
