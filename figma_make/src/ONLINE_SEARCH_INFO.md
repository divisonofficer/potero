# 온라인 검색 (Google Scholar API) 구현 가이드

## 현재 상태
현재는 **mock 데이터**를 사용하여 온라인 검색 결과를 시뮬레이션하고 있습니다.  
검색어를 입력하면 가상의 논문 3개가 "Available to Download" 섹션에 표시됩니다.

## Google Scholar API 사용의 문제점

### 1. **공식 API 없음**
- Google Scholar는 공식 API를 제공하지 않습니다
- HTML 스크래핑만 가능하지만 이는 Google의 서비스 약관 위반일 수 있습니다

### 2. **CORS 문제**
```javascript
// 브라우저에서 직접 요청 시 CORS 에러 발생
fetch('https://scholar.google.com/scholar?q=online')
  .then(res => res.text())
  .catch(err => console.error('CORS Error:', err));
```

### 3. **Rate Limiting & Bot Detection**
- Google은 자동화된 요청을 감지하고 차단합니다
- CAPTCHA가 표시될 수 있습니다
- IP 기반 차단 가능

## 추천 솔루션

### Option 1: Supabase Edge Functions (권장)
서버리스 함수를 통해 백엔드에서 처리:

```typescript
// Supabase Edge Function: /functions/search-scholar/index.ts
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async (req) => {
  const { query } = await req.json()
  
  // 서버 사이드에서 HTML 파싱
  const response = await fetch(
    `https://scholar.google.com/scholar?q=${encodeURIComponent(query)}`
  )
  const html = await response.text()
  
  // HTML 파싱하여 결과 추출
  const results = parseScholarResults(html)
  
  return new Response(JSON.stringify(results), {
    headers: { "Content-Type": "application/json" }
  })
})
```

### Option 2: 서드파티 API 사용
**Serpapi** (유료, 무료 티어 있음):
```javascript
const API_KEY = 'your_api_key'
const response = await fetch(
  `https://serpapi.com/search?engine=google_scholar&q=${query}&api_key=${API_KEY}`
)
const data = await response.json()
const results = data.organic_results
```

**Google Custom Search API** (논문 검색에는 제한적):
```javascript
const API_KEY = 'your_api_key'
const CX = 'your_search_engine_id'
const response = await fetch(
  `https://www.googleapis.com/customsearch/v1?key=${API_KEY}&cx=${CX}&q=${query}`
)
```

### Option 3: Semantic Scholar API (권장 - 무료)
공식 학술 논문 API:
```javascript
const response = await fetch(
  `https://api.semanticscholar.org/graph/v1/paper/search?query=${query}&limit=10`
)
const data = await response.json()
const papers = data.data.map(paper => ({
  id: paper.paperId,
  title: paper.title,
  authors: paper.authors.map(a => a.name),
  year: paper.year,
  citations: paper.citationCount,
  pdfUrl: paper.openAccessPdf?.url
}))
```

### Option 4: CrossRef API (무료)
DOI 기반 논문 검색:
```javascript
const response = await fetch(
  `https://api.crossref.org/works?query=${query}&rows=10`
)
const data = await response.json()
```

## 구현 예시 (Semantic Scholar 사용)

```typescript
// /utils/scholarSearch.ts
export async function searchOnlinePapers(query: string) {
  try {
    const response = await fetch(
      `https://api.semanticscholar.org/graph/v1/paper/search?query=${encodeURIComponent(query)}&limit=10&fields=title,authors,year,citationCount,openAccessPdf,publicationVenue`
    )
    
    if (!response.ok) throw new Error('Search failed')
    
    const data = await response.json()
    
    return data.data.map((paper: any) => ({
      id: paper.paperId,
      title: paper.title,
      authors: paper.authors?.map((a: any) => a.name) || [],
      year: paper.year || 2024,
      conference: paper.publicationVenue?.name || 'Unknown',
      citations: paper.citationCount || 0,
      pdfUrl: paper.openAccessPdf?.url || '#',
      scholarUrl: `https://www.semanticscholar.org/paper/${paper.paperId}`
    }))
  } catch (error) {
    console.error('Online search error:', error)
    return []
  }
}
```

## 현재 코드 수정 방법

`/components/PdfLibrary.tsx`에서:

```typescript
// 기존 mock 함수를 실제 API 호출로 변경
const getOnlineResults = async (query: string): Promise<OnlinePaper[]> => {
  if (query.length < 3) return []
  
  // Semantic Scholar API 사용
  return await searchOnlinePapers(query)
}

// useMemo를 useEffect + useState로 변경 필요
const [onlineResults, setOnlineResults] = useState<OnlinePaper[]>([])

useEffect(() => {
  if (searchQuery.length >= 3) {
    searchOnlinePapers(searchQuery).then(setOnlineResults)
  } else {
    setOnlineResults([])
  }
}, [searchQuery])
```

## 결론

**권장 방법: Semantic Scholar API**
- 무료
- 공식 API
- 풍부한 메타데이터
- Rate limit: 100 requests/5분

Google Scholar HTML 파싱은:
- 기술적으로 가능하지만
- 법적/윤리적 문제 가능성
- 유지보수 어려움 (HTML 구조 변경 시)
- 프로덕션 환경에서는 비권장

**현재는 mock 데이터로 UI/UX를 완성하고,  
실제 배포 시 위 API 중 하나를 선택하여 통합하는 것을 권장합니다.**
