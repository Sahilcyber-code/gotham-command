# Google Cloud OAuth 2.0 Setup Guide for Gotham Command

This guide explains how to obtain and configure Google OAuth 2.0 credentials for Gotham Command local development and deployment.

---

## Prerequisites

- A Google Account
- Access to the [Google Cloud Console](https://console.cloud.google.com/)

---

## Step-by-Step Configuration

### 1. Create or Select a Project
1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Click the project dropdown in the top navigation bar.
3. Select an existing project or click **New Project**, enter `Gotham Command`, and click **Create**.

### 2. Configure the OAuth Consent Screen
1. In the left navigation menu, go to **APIs & Services** > **OAuth consent screen**.
2. Select **External** user type and click **Create**.
3. Fill in required application information:
   - **App name**: `Gotham Command`
   - **User support email**: Your email address
   - **Developer contact information**: Your email address
4. Click **Save and Continue**.
5. In the **Scopes** step, click **Add or Remove Scopes** and select:
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
   - `openid`
6. Click **Save and Continue**.
7. In the **Test users** step, add the Google accounts you will use for testing.
8. Click **Save and Continue** to review the summary.

### 3. Create OAuth 2.0 Client Credentials
1. In the left navigation menu, go to **APIs & Services** > **Credentials**.
2. Click **+ Create Credentials** at the top and select **OAuth client ID**.
3. In the **Application type** dropdown, choose **Web application**.
4. Set the **Name** to `Gotham Command Local`.
5. Under **Authorized JavaScript origins**, add:
   - `http://localhost:8080`
   - `http://localhost:5173`
6. Under **Authorized redirect URIs**, add:
   - `http://localhost:8080/login/oauth2/code/google`
7. Click **Create**.

### 4. Configure Environment Variables
Copy your **Client ID** and **Client Secret** into your environment or `.env` file (do **NOT** commit these to source control):

```bash
GOOGLE_CLIENT_ID=your_client_id_here.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_client_secret_here
GOOGLE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google
```

---

## Initiation & Callback Endpoints

- **Initiate Google Login**:
  Navigate to:
  `http://localhost:8080/oauth2/authorization/google`
- **OAuth 2.0 Callback (Backend)**:
  Handled automatically by Spring Security at:
  `http://localhost:8080/login/oauth2/code/google`
- **Frontend Callback Redirection**:
  After successful authentication, the backend sets an `HttpOnly` refresh token cookie and redirects to:
  `http://localhost:5173/auth/callback?token=<access_token>`

---

## Security Best Practices

- Never commit real client secrets to Git.
- Use distinct OAuth Client credentials for local development, staging, and production.
- For production, ensure `REFRESH_COOKIE_SECURE=true` and update authorized redirect URIs to use HTTPS.
