import { useState } from 'react';
import { PdfLibrary } from './components/PdfLibrary';
import { PdfViewer } from './components/PdfViewer';
import { KnowledgeMap } from './components/KnowledgeMap';
import { ChatbotPanel } from './components/ChatbotPanel';
import { DetailedInfoView } from './components/DetailedInfoView';
import { Settings } from './components/Settings';
import { TabBar, Tab } from './components/TabBar';
import { mockPdfData } from './data/mockPdfData';
import { Toaster } from 'sonner@2.0.3';

export interface PdfFile {
  id: string;
  title: string;
  authors: string[];
  year: number;
  conference: string;
  subject: string[];
  abstract: string;
  pdfUrl: string;
  thumbnailUrl: string;
  citations: number;
  doi: string;
  hasBlogView?: boolean; // Whether blog view is available for this PDF
}

export type ViewMode = 'library' | 'viewer' | 'knowledge-map';

export default function App() {
  const [tabs, setTabs] = useState<Tab[]>([
    {
      id: 'home',
      type: 'home',
      title: 'Library'
    }
  ]);
  const [activeTabId, setActiveTabId] = useState('home');
  const [isChatOpen, setIsChatOpen] = useState(false);

  const activeTab = tabs.find(tab => tab.id === activeTabId);

  const handleOpenPdf = (pdf: PdfFile) => {
    // Check if PDF is already open in a tab
    const existingTab = tabs.find(tab => tab.type === 'viewer' && tab.pdf?.id === pdf.id);
    
    if (existingTab) {
      setActiveTabId(existingTab.id);
    } else {
      // Create new tab
      const newTab: Tab = {
        id: `pdf-${pdf.id}-${Date.now()}`,
        type: 'viewer',
        title: pdf.title.length > 30 ? pdf.title.slice(0, 30) + '...' : pdf.title,
        pdf
      };
      setTabs([...tabs, newTab]);
      setActiveTabId(newTab.id);
    }
  };

  const handleOpenKnowledgeMap = (pdf?: PdfFile) => {
    // Check if knowledge map tab already exists
    const existingTab = tabs.find(tab => tab.type === 'knowledge-map');
    
    if (existingTab) {
      setActiveTabId(existingTab.id);
    } else {
      const newTab: Tab = {
        id: `km-${Date.now()}`,
        type: 'knowledge-map',
        title: 'Knowledge Map',
        pdf
      };
      setTabs([...tabs, newTab]);
      setActiveTabId(newTab.id);
    }
  };

  const handleOpenDetailedInfo = (type: 'author' | 'conference' | 'doi', data: string) => {
    // Check if this detailed info is already open
    const existingTab = tabs.find(
      tab => tab.type === 'detailed-info' && tab.infoType === type && tab.infoData === data
    );
    
    if (existingTab) {
      setActiveTabId(existingTab.id);
    } else {
      const titlePrefix = type === 'author' ? '👤' : type === 'conference' ? '🏛️' : '🔗';
      const displayData = data.length > 20 ? data.slice(0, 20) + '...' : data;
      const newTab: Tab = {
        id: `info-${type}-${Date.now()}`,
        type: 'detailed-info',
        title: `${titlePrefix} ${displayData}`,
        infoType: type,
        infoData: data
      };
      setTabs([...tabs, newTab]);
      setActiveTabId(newTab.id);
    }
  };

  const handleOpenSettings = () => {
    // Check if settings tab already exists
    const existingTab = tabs.find(tab => tab.type === 'settings');
    
    if (existingTab) {
      setActiveTabId(existingTab.id);
    } else {
      const newTab: Tab = {
        id: `settings-${Date.now()}`,
        type: 'settings',
        title: '⚙️ Settings'
      };
      setTabs([...tabs, newTab]);
      setActiveTabId(newTab.id);
    }
  };

  const handleTabClose = (tabId: string) => {
    if (tabId === 'home') return; // Can't close home tab
    
    const newTabs = tabs.filter(tab => tab.id !== tabId);
    setTabs(newTabs);
    
    // If closing active tab, switch to the previous tab or home
    if (tabId === activeTabId) {
      const closedIndex = tabs.findIndex(tab => tab.id === tabId);
      const newActiveTab = newTabs[Math.max(0, closedIndex - 1)];
      setActiveTabId(newActiveTab.id);
    }
  };

  const handleBackToLibrary = () => {
    setActiveTabId('home');
  };

  return (
    <div className="h-screen w-full bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50 overflow-hidden flex flex-col">
      {/* Toast Notifications */}
      <Toaster 
        position="top-right" 
        richColors 
        expand={true}
        closeButton
      />

      {/* Tab Bar */}
      <TabBar
        tabs={tabs}
        activeTabId={activeTabId}
        onTabChange={setActiveTabId}
        onTabClose={handleTabClose}
        onSearchSelect={handleOpenPdf}
        onOpenKnowledgeMap={() => handleOpenKnowledgeMap(activeTab?.pdf)}
        onOpenSettings={handleOpenSettings}
        onToggleChat={() => setIsChatOpen(!isChatOpen)}
        isChatOpen={isChatOpen}
        allPdfs={mockPdfData}
      />

      {/* Tab Content */}
      <div className="flex-1 overflow-hidden relative">
        {activeTab?.type === 'home' && (
          <PdfLibrary 
            onOpenPdf={handleOpenPdf} 
            onOpenKnowledgeMap={handleOpenKnowledgeMap}
            onOpenDetailedInfo={handleOpenDetailedInfo}
          />
        )}
        
        {activeTab?.type === 'viewer' && activeTab.pdf && (
          <PdfViewer 
            pdf={activeTab.pdf}
            isChatbotOpen={isChatOpen}
            onOpenKnowledgeMap={() => handleOpenKnowledgeMap(activeTab.pdf)}
            onOpenDetailedInfo={handleOpenDetailedInfo}
          />
        )}
        
        {activeTab?.type === 'knowledge-map' && (
          <KnowledgeMap 
            selectedPdf={activeTab.pdf || null}
            onBack={handleBackToLibrary}
          />
        )}

        {activeTab?.type === 'detailed-info' && activeTab.infoType && activeTab.infoData && (
          <DetailedInfoView
            type={activeTab.infoType}
            data={activeTab.infoData}
            onBack={handleBackToLibrary}
          />
        )}

        {activeTab?.type === 'settings' && (
          <Settings />
        )}

        {/* Inline Chat Panel - appears on right side when toggled */}
        {isChatOpen && (
          <ChatbotPanel pdf={activeTab?.pdf || null} onClose={() => setIsChatOpen(false)} />
        )}
      </div>
    </div>
  );
}