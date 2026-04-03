# Potero - Claude Code 가이드

## 프로젝트 개요

Potero는 로컬 중심의 논문 관리 및 읽기 보조 데스크톱 앱이다.
PDF 드래그앤드롭, DOI/arXiv/온라인 검색으로 논문을 가져오고, 메타데이터 정리·PDF 확보·인용 추출·AI 대화를 한 흐름으로 제공한다.
"모으는 도구"가 아니라 "읽기 쉽게 바꾸는 도구"가 목표다.

## 저장소 구조

```
potero/
├── potero-svelte/       # 프론트엔드: SvelteKit 5 + Svelte 5 + Tailwind CSS 4 + Electron
├── potero-kmp/
│   ├── server/          # Ktor 서버 (포트 18080), API 라우트
│   ├── shared/          # 도메인 모델, 서비스 (LLM/PDF/메타데이터/narrative)
│   ├── database/        # SQLDelight 스키마 (SQLite, 22개 테이블)
│   └── android-sdk/     # 향후 Android 확장용 (현재 비어 있음)
├── figma_make/          # UI 프로토타입 번들
├── docs/                # 기능 문서
└── scripts/             # 운영 보조 스크립트
```

## 기술 스택

| 영역 | 기술 |
|------|------|
| 프론트엔드 | SvelteKit 2, Svelte 5, Tailwind CSS 4, Electron 33, TypeScript |
| 백엔드 | Kotlin Multiplatform, Ktor 3, Koin (DI), Kotlinx Serialization |
| 데이터베이스 | SQLDelight, SQLite |
| PDF 처리 | PDFBox, GROBID, pdfjs-dist |
| AI | POSTECH GenAI (SSO 토큰 기반), SSE 스트리밍, tool calling |
| 학술 데이터 | Semantic Scholar, OpenAlex, PubMed, DBLP, Unpaywall, DOI/arXiv resolver |
| 빌드 | Vite 6, Gradle Kotlin DSL, electron-builder |

## 주요 실행 명령어

```bash
# 개발
cd potero-svelte && npm run dev                    # 프론트만
cd potero-kmp && ./gradlew :server:run             # 백엔드만
./start_all.sh                                     # 프론트 + 백엔드 동시

# Electron
cd potero-svelte && npm run electron:dev           # Electron (외부 백엔드)
cd potero-svelte && npm run electron:dev:backend   # Electron + 로컬 백엔드

# 빌드
./build-windows.sh                                 # Windows 배포 빌드
cd potero-svelte && npm run dist                   # 배포 빌드
cd potero-svelte && npm run dist:full              # GROBID 포함 전체 빌드
```

## 아키텍처

```
브라우저/Electron (localhost:5173)
    ↓  Vite 프록시: /api → 127.0.0.1:18080
Ktor 서버 (18080)
    ↓
SQLite DB (로컬)
    ↓
외부 API (POSTECH GenAI, Semantic Scholar 등)
```

**주의**: 포트는 `18080`이다. 일부 오래된 문서나 스크립트에 `8080`이 남아 있을 수 있으니 혼동하지 말 것.

## 프론트엔드 핵심 파일

- [potero-svelte/src/routes/+page.svelte](potero-svelte/src/routes/+page.svelte) — 메인 UI 진입점
- [potero-svelte/src/lib/api/client.ts](potero-svelte/src/lib/api/client.ts) — 백엔드 API 클라이언트 (모든 HTTP 통신)
- [potero-svelte/src/lib/stores/](potero-svelte/src/lib/stores/) — 상태 관리 (library, tabs, notes, relatedWork, jobs 등)
- [potero-svelte/src/lib/types/index.ts](potero-svelte/src/lib/types/index.ts) — 공유 TypeScript 타입
- [potero-svelte/src/lib/components/](potero-svelte/src/lib/components/) — UI 컴포넌트 (59개)

## 백엔드 핵심 파일

- `potero-kmp/server/src/main/kotlin/com/potero/server/Application.kt` — 서버 진입점
- `potero-kmp/server/src/main/kotlin/com/potero/server/plugins/Routing.kt` — API 라우팅
- `potero-kmp/server/src/main/kotlin/com/potero/server/di/ServiceLocator.kt` — 의존성 주입
- `potero-kmp/database/src/commonMain/sqldelight/` — SQLDelight 스키마

## API 라우트 목록 (prefix: `/api`)

| 라우트 | 기능 |
|--------|------|
| `/papers` | 논문 CRUD, DOI/arXiv import, PDF 다운로드 |
| `/chat/*` | 채팅, SSE 스트리밍, 세션 관리 |
| `/search` | 멀티소스 검색 |
| `/tags` | 태그 관리 |
| `/authors` | 저자 프로필 |
| `/related-work` | 관련 논문 탐색, 비교 테이블 |
| `/narratives` | Reddit 스타일 논문 설명 생성 |
| `/notes` | 연구 노트 (블록 기반) |
| `/upload` | PDF 업로드 및 분석 |
| `/settings` | 사용자 설정, API 키 관리 |
| `/jobs` | 백그라운드 작업 상태 |
| `/papers/{id}/figures` | 그림 추출 |
| `/papers/{id}/tables` | 표 추출 |
| `/papers/{id}/citations` | 인용 추출 |

## 주요 데이터 모델

**핵심 테이블**: Paper, Author, Tag, Note, ChatMessage, ChatSession, ResearchNote

**PDF 분석**: PdfPreprocessing, Reference, Citation, Figure, PdfTable, PdfEquation, GrobidReference

**AI**: Narrative, RelatedWork, ComparisonTable

**기타**: Settings, PersonMention, CitationSpan

## 현재 상태 및 주의사항

- submission UI는 mock 데이터 중심 프로토타입 단계 — 실제 기능 아님
- `android-sdk` 모듈은 구조만 있고 본격 구현 전
- 자동화 테스트 부재 — 기능 수정 시 수동 검증 필요
- 일부 오래된 스크립트/문서에 포트 `8080` 잔재 → 실제 포트는 `18080`

## AI / LLM 관련

- POSTECH GenAI (`https://genai.postech.ac.kr`) SSO 토큰으로 인증
- 채팅은 SSE 스트리밍 + tool calling 지원
- `PostechLLMService`, `ChatService`, `GenAIFileUploadService`가 핵심 서비스
- LLM 기반 메타데이터 정제(`MetadataCleaningService`), 태그 추천, narrative 생성 등에 사용


# Potero development rules

## Product goal
Potero is a research paper reader/manager.
Current strategic priority is improving in-reader AI UX in the PDF viewer.

## Current product strengths
- related work generation
- narrative generation
- metadata enrichment
- author/journal profile views
- local library workflow
- submission workflow
- GROBID-based citation/reference extraction

## Current product weakness
- weak PDF-reader-native AI UX
- too many useful AI capabilities are hidden behind tabs or separate views

## Priority order
1. Auto Highlight
2. Quick Summary card in InspectorPanel
3. Smart Citation Card
4. Grounded answer highlighting
5. Inline Translation
6. Keyword/Term explanation
7. Better preset prompts
8. Figure/Table/Equation explanation

## Engineering principles
- Prefer incremental PRs
- Reuse existing stores, APIs, and components
- Do not introduce large refactors without strong justification
- Keep PdfViewer scroll/render performance stable
- New features must degrade gracefully
- Always propose plan + risks + tests before coding

## Output format for complex tasks
For every non-trivial request, first provide:
1. relevant files
2. current architecture summary
3. implementation plan
4. risks
5. test plan
6. smallest shippable PR scope