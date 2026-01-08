# Potero Electron SSO 로그인

## 개요

POSTECH GenAI SSO 로그인을 자동화하여 사용자가 로그인 창에서 로그인만 하면 `access_token`을 자동으로 추출하여 백엔드에 저장합니다.

## 실행 방법

### 1. Electron 앱 실행 (권장)

```bash
npm run electron:dev
```

**동작 방식:**
1. Settings 탭으로 이동
2. "Login with SSO" 버튼 클릭
3. 팝업 창이 열리며 GenAI 로그인 페이지 표시
4. POSTECH 계정으로 로그인
5. 자동으로 토큰이 추출되어 저장됨
6. 팝업 창이 닫히고 SSO 연결 상태로 표시됨

### 2. 웹 브라우저 실행 (Fallback)

```bash
npm run dev
```

**동작 방식:**
1. Settings 탭으로 이동
2. "Login with SSO" 버튼 클릭
3. GenAI 로그인 페이지로 리다이렉트
4. POSTECH 계정으로 로그인
5. Callback URL로 돌아오면서 자동으로 토큰 저장
6. Settings 탭으로 리다이렉트

**참고:** GenAI가 custom `redirect_uri`를 지원하지 않으면, "Save Token Manually" 버튼으로 수동 입력해야 할 수 있습니다.

## 구현 세부사항

### Electron Main Process (`main.js`)

- **ES Module 방식** 사용
- `ipcMain.handle('sso-login')`: SSO 로그인 처리
- BrowserWindow로 GenAI 로그인 페이지 열기
- URL 변경 감지 (`will-navigate`, `did-navigate`, `did-navigate-in-page`)
- Callback URL의 fragment에서 `access_token` 자동 추출
- 5분 타임아웃 및 에러 핸들링

### Preload Script (`preload.cjs`)

- **CommonJS 방식** 사용 (Electron 요구사항)
- `contextBridge`로 IPC API를 renderer process에 안전하게 노출
- `window.electronAPI.loginSSO()` 제공
- `window.electronAPI.isElectron` 플래그 제공

### Callback Route (`/auth/callback`)

- 웹 브라우저 환경에서 SSO callback 처리
- URL fragment에서 토큰 파싱
- 자동으로 백엔드에 저장
- 시각적 피드백 (로딩, 성공, 에러)

## 보안

- **Context Isolation**: Preload 스크립트에서 context isolation 활성화
- **Node Integration 비활성화**: renderer process에서 Node.js API 접근 차단
- **IPC API만 노출**: 필요한 API만 `contextBridge`를 통해 안전하게 노출
- **토큰 마스킹**: Settings 조회 시 API 키 마스킹 (`****`)

## 파일 첨부 기능

SSO 로그인 완료 후 Chat에서 파일 첨부 가능:

1. Chat Panel에서 📎 (Paperclip) 버튼 클릭
2. 파일 선택 (PDF, 이미지, 코드 등)
3. 파일이 GenAI 서버에 업로드됨
4. 메시지 전송 시 파일이 LLM에 첨부됨

**GenAI API Endpoint:**
```
POST https://genai.postech.ac.kr/v2/athena/chats/m1/files?site_name={siteName}
Authorization: Bearer {sso_token}
```

## 트러블슈팅

### Electron이 시작되지 않을 때

```bash
# 의존성 재설치
rm -rf node_modules package-lock.json
npm install

# Electron 캐시 삭제
rm -rf ~/.cache/electron
```

### SSO 토큰 추출이 실패할 때

1. 브라우저 콘솔에서 Callback URL 확인:
   ```
   https://genai.postech.ac.kr/auth/callback#access_token=...&expires_in=3600
   ```
2. URL fragment에 `access_token`이 있는지 확인
3. 없으면 "Save Token Manually" 버튼 사용

### CORS 오류가 발생할 때

- Electron 환경에서는 CORS 제한 없음
- 웹 브라우저에서는 백엔드 프록시 사용 (`/api` → `http://127.0.0.1:8080`)

## 향후 개선

- [ ] SSO 토큰 자동 갱신 (refresh token)
- [ ] 파일 업로드 진행률 표시
- [ ] 드래그 앤 드롭으로 파일 첨부
- [ ] 여러 파일 동시 첨부
- [ ] 파일 업로드 실패 시 재시도

## 참고 자료

- [Electron IPC](https://www.electronjs.org/docs/latest/tutorial/ipc)
- [Electron Security](https://www.electronjs.org/docs/latest/tutorial/security)
- [Context Isolation](https://www.electronjs.org/docs/latest/tutorial/context-isolation)
