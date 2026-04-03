# Potero

## 한 줄 소개

Potero는 로컬 중심의 논문 관리 및 읽기 보조 도구다. PDF를 직접 넣거나 DOI, arXiv, 온라인 검색으로 논문을 가져오고, 메타데이터 정리, PDF 확보, 인용/레퍼런스 추출, AI 요약과 대화까지 한 흐름으로 묶는 것을 목표로 한다.

## 왜 만드는가

- 논문을 읽는 과정 자체를 어렵게 느끼는 사람도 논문을 더 쉽게 이해하고 정리할 수 있게 한다.
- 단순한 서지 관리보다 "읽기", "설명", "맥락화", "정리"에 더 무게를 둔다.
- 장기적으로는 데스크톱 앱과 Android 앱이 같은 코어를 공유하도록 설계하려고 한다.

## 현재 구현 범위

### 1. 라이브러리와 가져오기

- PDF 드래그앤드롭 업로드
- DOI import
- arXiv import
- 온라인 논문 검색 후 바로 import
- PDF 저장 경로 설정 및 로컬 보관
- 썸네일 생성과 기본 메타데이터 관리

### 2. PDF 분석 파이프라인

- GROBID 기반 citation/reference 추출
- OCR 및 PDF 전처리 캐시
- figure, table, formula 추출
- 재분석, 재추출, bulk reanalyze 작업
- preprocessing 상태 조회 및 job 추적

### 3. AI 기능

- 논문별 chat 및 SSE 스트리밍 응답
- POSTECH GenAI SSO 로그인과 파일 첨부 업로드
- 논문 narrative 생성
- Reddit thread 스타일 설명 생성 및 export
- 자동 태깅, 태그 추천, 태그 병합
- related work 후보 탐색 및 comparison table 생성

### 4. 노트와 탐색

- 연구 노트 CRUD
- note backlink 및 검색
- 논문과 연결된 floating note panel
- author, journal, tag 프로필 뷰

### 5. UI와 데스크톱 경험

- Svelte 기반 라이브러리, 뷰어, inspector, sidebar UI
- onboarding wizard
- Electron 기반 데스크톱 실행
- Windows 배포용 JRE 번들링
- GROBID 번들 포함 빌드 경로 지원

## 저장소 구조

- `potero-svelte`: SvelteKit + Svelte 5 + Tailwind + Electron 프런트엔드
- `potero-kmp/server`: Ktor 서버, API 라우트, 정적 파일 서빙
- `potero-kmp/shared`: 도메인 모델, repository, LLM/metadata/pdf/narrative 서비스
- `potero-kmp/database`: SQLDelight 스키마와 로컬 DB 계층
- `figma_make`: 별도 UI 프로토타입 번들
- `docs`: 기능 문서
- `scripts`: 운영/패치 보조 스크립트

## 핵심 기술 스택

- 프런트엔드: SvelteKit 2, Svelte 5, Tailwind CSS 4, Electron
- 백엔드: Kotlin Multiplatform, Kotlin/JVM, Ktor 3
- 데이터 저장: SQLDelight, SQLite
- PDF 처리: PDFBox, GROBID, OCR 파이프라인
- AI/외부 연동: POSTECH GenAI, Semantic Scholar, OpenAlex, PubMed, DBLP, DOI/arXiv resolver
- PDF 확보: Unpaywall, CVF Open Access, 선택적 Sci-Hub 연동

## 주요 실행 경로

- 프런트 개발: `cd potero-svelte && npm run dev`
- Electron 개발: `cd potero-svelte && npm run electron:dev`
- Electron + 로컬 백엔드 실행: `cd potero-svelte && npm run electron:dev:backend`
- 백엔드 실행: `cd potero-kmp && ./gradlew :server:run`
- 헬퍼 스크립트: `./start_back.sh`, `./start_front.sh`, `./start_all.sh`
- Windows 빌드: `./build-windows.sh`
- 배포 빌드: `cd potero-svelte && npm run dist`
- GROBID 포함 전체 빌드: `cd potero-svelte && npm run dist:full`

## 현재 코드베이스 기준 메모

- 실제 Ktor 서버 포트와 Vite 프록시는 `18080` 기준이다.
- 일부 보조 스크립트와 README에는 아직 `8080` 설명이 남아 있어 문서 정리가 더 필요하다.
- `potero-kmp`에는 Android 확장을 염두에 둔 흔적이 있지만, 현재 `android-sdk` 모듈은 비어 있어 아직 본격 구현 단계는 아니다.
- submission 관련 UI는 저장소에 존재하지만 현재는 mock 데이터 중심의 프로토타입 성격이 강하다.
- 자동화 테스트는 저장소 기준으로 아직 두드러지지 않으므로 기능 확장 시 검증 체계를 보강할 필요가 있다.

## 프로젝트 방향

- Zotero처럼 모으는 도구를 넘어서, 논문을 "읽기 쉽게 바꾸는 도구"로 가는 것이 핵심이다.
- 논문 PDF, 메타데이터, 인용 관계, 노트, AI 설명을 분리하지 않고 하나의 작업 흐름으로 다룬다.
- 장기적으로는 공통 Kotlin 코어를 활용해 Android까지 확장할 수 있는 구조를 지향한다.
