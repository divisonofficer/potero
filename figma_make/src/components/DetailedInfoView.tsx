import { ArrowLeft, TrendingUp, Calendar, FileText, BookOpen, Building, User, Globe, Mail, ExternalLink } from 'lucide-react';

interface DetailedInfoViewProps {
  type: 'author' | 'conference' | 'doi';
  data: string;
  onBack: () => void;
}

export function DetailedInfoView({ type, data, onBack }: DetailedInfoViewProps) {
  const getDetailedContent = () => {
    switch (type) {
      case 'author':
        return {
          icon: <User className="w-8 h-8" />,
          title: data,
          subtitle: 'Researcher Profile',
          heroGradient: 'from-blue-600 to-indigo-600',
          stats: [
            { label: 'Total Publications', value: '127', icon: <FileText className="w-5 h-5" /> },
            { label: 'Total Citations', value: '12,456', icon: <TrendingUp className="w-5 h-5" /> },
            { label: 'h-index', value: '42', icon: <BookOpen className="w-5 h-5" /> },
            { label: 'i10-index', value: '89', icon: <BookOpen className="w-5 h-5" /> },
          ],
          sections: [
            {
              title: 'About',
              content: data + ' is a leading researcher in the field of machine learning and artificial intelligence. Their work has significantly contributed to the advancement of deep learning architectures, particularly in the areas of attention mechanisms and transformer models. With over a decade of research experience, they have published extensively in top-tier conferences and journals.',
            },
            {
              title: 'Research Interests',
              items: ['Deep Learning', 'Natural Language Processing', 'Computer Vision', 'Reinforcement Learning', 'Neural Architecture Search', 'Transfer Learning'],
            },
            {
              title: 'Current Affiliations',
              items: ['Stanford University - Professor', 'Google AI - Research Scientist', 'OpenAI - Advisor'],
            },
            {
              title: 'Education',
              items: [
                'Ph.D. in Computer Science - MIT (2015)',
                'M.S. in Artificial Intelligence - Stanford University (2012)',
                'B.S. in Computer Science - UC Berkeley (2010)',
              ],
            },
            {
              title: 'Awards & Honors',
              items: [
                'Best Paper Award - NeurIPS 2024',
                'ACM SIGKDD Innovation Award 2023',
                'Google Faculty Research Award 2022',
                'NSF CAREER Award 2021',
              ],
            },
          ],
          publications: Array.from({ length: 12 }, (_, i) => ({
            title: `Advanced Deep Learning Techniques for ${['Computer Vision', 'Natural Language Processing', 'Reinforcement Learning'][i % 3]} ${i + 1}`,
            year: 2024 - Math.floor(i / 3),
            conference: ['CVPR', 'NeurIPS', 'ICLR', 'ICML'][i % 4],
            citations: 1234 - i * 100,
          })),
        };
      
      case 'conference':
        return {
          icon: <Building className="w-8 h-8" />,
          title: data,
          subtitle: 'Conference Information',
          heroGradient: 'from-purple-600 to-pink-600',
          stats: [
            { label: 'Acceptance Rate', value: '23%', icon: <TrendingUp className="w-5 h-5" /> },
            { label: 'Submissions (2024)', value: '5,413', icon: <FileText className="w-5 h-5" /> },
            { label: 'Accepted Papers', value: '1,245', icon: <BookOpen className="w-5 h-5" /> },
            { label: 'Impact Factor', value: '8.7', icon: <TrendingUp className="w-5 h-5" /> },
          ],
          sections: [
            {
              title: 'About the Conference',
              content: data + ' is the premier international conference on computer vision and pattern recognition. It brings together researchers and practitioners from academia and industry to present their latest work on visual recognition, image understanding, and related topics. The conference features technical sessions, workshops, tutorials, and exhibits.',
            },
            {
              title: 'Key Topics',
              items: [
                'Deep Learning for Computer Vision',
                'Image Recognition and Classification',
                'Object Detection and Segmentation',
                'Video Understanding',
                '3D Vision and Reconstruction',
                'Visual Recognition in the Wild',
                'Generative Models for Vision',
                'Vision and Language',
              ],
            },
            {
              title: 'Conference Details',
              items: [
                'Location: Seattle, WA, USA',
                'Date: June 17-21, 2026',
                'Venue: Washington State Convention Center',
                'Expected Attendance: 10,000+',
              ],
            },
            {
              title: 'Important Dates',
              items: [
                'Paper Submission Deadline: November 15, 2025',
                'Supplementary Material Deadline: November 22, 2025',
                'Notification to Authors: February 28, 2026',
                'Camera Ready Deadline: March 31, 2026',
              ],
            },
          ],
          publications: Array.from({ length: 12 }, (_, i) => ({
            title: `${data} 2024 - ${['Vision Transformers', 'Self-Supervised Learning', 'Diffusion Models', 'Few-Shot Learning'][i % 4]} Research Paper ${i + 1}`,
            year: 2024 - Math.floor(i / 6),
            conference: data,
            citations: 890 - i * 50,
          })),
        };
      
      case 'doi':
        return {
          icon: <ExternalLink className="w-8 h-8" />,
          title: data,
          subtitle: 'Digital Object Identifier',
          heroGradient: 'from-green-600 to-teal-600',
          stats: [
            { label: 'Citations', value: '234', icon: <TrendingUp className="w-5 h-5" /> },
            { label: 'Published', value: '2024', icon: <Calendar className="w-5 h-5" /> },
            { label: 'Views', value: '5,678', icon: <FileText className="w-5 h-5" /> },
            { label: 'Downloads', value: '1,234', icon: <BookOpen className="w-5 h-5" /> },
          ],
          sections: [
            {
              title: 'About DOI',
              content: 'A Digital Object Identifier (DOI) is a persistent identifier or handle used to uniquely identify objects, standardized by the International Organization for Standardization (ISO). DOIs are widely used for academic papers, datasets, and other scholarly resources to ensure permanent access.',
            },
            {
              title: 'Access Information',
              items: [
                'Publisher: IEEE',
                'Publication Type: Conference Paper',
                'License: Open Access (CC BY 4.0)',
                'Language: English',
              ],
            },
          ],
          links: [
            { label: 'DOI.org', url: `https://doi.org/${data}`, description: 'Official DOI resolver' },
            { label: 'Google Scholar', url: `https://scholar.google.com/scholar?q=${encodeURIComponent(data)}`, description: 'Search on Google Scholar' },
            { label: 'Semantic Scholar', url: `https://www.semanticscholar.org/search?q=${encodeURIComponent(data)}`, description: 'AI-powered research tool' },
            { label: 'CrossRef', url: `https://search.crossref.org/?q=${encodeURIComponent(data)}`, description: 'Metadata search' },
            { label: 'ResearchGate', url: `https://www.researchgate.net/search?q=${encodeURIComponent(data)}`, description: 'Research network' },
            { label: 'PubMed', url: `https://pubmed.ncbi.nlm.nih.gov/?term=${encodeURIComponent(data)}`, description: 'Biomedical literature' },
          ],
          publications: Array.from({ length: 8 }, (_, i) => ({
            title: `Citing Paper ${i + 1}: Advanced Methods in Deep Learning`,
            year: 2024,
            conference: ['CVPR', 'NeurIPS', 'ICLR'][i % 3],
            citations: 123 - i * 10,
          })),
        };
    }
  };

  const content = getDetailedContent();

  return (
    <div className="h-full overflow-y-auto bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50">
      {/* Hero Section */}
      <div className={`bg-gradient-to-r ${content.heroGradient} text-white`}>
        <div className="max-w-7xl mx-auto px-8 py-12">
          <button
            onClick={onBack}
            className="flex items-center gap-2 mb-6 px-3 py-2 bg-white/20 hover:bg-white/30 rounded-lg transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back
          </button>
          <div className="flex items-center gap-4 mb-6">
            {content.icon}
            <div>
              <p className="text-white/80 text-sm mb-1">{content.subtitle}</p>
              <h1 className="text-4xl font-bold">{content.title}</h1>
            </div>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-8 py-8">
        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          {content.stats.map((stat, idx) => (
            <div key={idx} className="bg-white rounded-2xl p-6 shadow-sm border border-gray-200">
              <div className="flex items-center gap-3 mb-3 text-indigo-600">
                {stat.icon}
                <span className="text-sm text-gray-600">{stat.label}</span>
              </div>
              <div className="text-3xl font-bold text-gray-900">{stat.value}</div>
            </div>
          ))}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Main Content */}
          <div className="lg:col-span-2 space-y-6">
            {content.sections?.map((section, idx) => (
              <div key={idx} className="bg-white rounded-2xl p-6 shadow-sm border border-gray-200">
                <h2 className="text-xl font-bold text-gray-900 mb-4">{section.title}</h2>
                {section.content && (
                  <p className="text-gray-700 leading-relaxed">{section.content}</p>
                )}
                {section.items && (
                  <ul className="space-y-2">
                    {section.items.map((item, i) => (
                      <li key={i} className="flex items-start gap-3">
                        <div className="w-1.5 h-1.5 bg-indigo-600 rounded-full mt-2 flex-shrink-0"></div>
                        <span className="text-gray-700">{item}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}

            {/* External Links for DOI */}
            {type === 'doi' && content.links && (
              <div className="bg-white rounded-2xl p-6 shadow-sm border border-gray-200">
                <h2 className="text-xl font-bold text-gray-900 mb-4">External Resources</h2>
                <div className="grid gap-3">
                  {content.links.map((link, idx) => (
                    <a
                      key={idx}
                      href={link.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-between p-4 bg-gradient-to-r from-gray-50 to-indigo-50 hover:from-indigo-50 hover:to-purple-50 rounded-xl transition-all group border border-gray-200"
                    >
                      <div>
                        <div className="font-medium text-gray-900 mb-1">{link.label}</div>
                        <div className="text-sm text-gray-600">{link.description}</div>
                      </div>
                      <ExternalLink className="w-5 h-5 text-gray-400 group-hover:text-indigo-600 transition-colors" />
                    </a>
                  ))}
                </div>
              </div>
            )}

            {/* Publications/Papers */}
            <div className="bg-white rounded-2xl p-6 shadow-sm border border-gray-200">
              <h2 className="text-xl font-bold text-gray-900 mb-4">
                {type === 'doi' ? 'Citing Papers' : type === 'author' ? 'Publications' : 'Recent Papers'}
              </h2>
              <div className="space-y-3">
                {content.publications?.map((paper, idx) => (
                  <div key={idx} className="p-4 bg-gradient-to-r from-gray-50 to-blue-50 rounded-xl hover:from-blue-50 hover:to-indigo-50 transition-all cursor-pointer border border-gray-200">
                    <h3 className="font-medium text-gray-900 mb-2">{paper.title}</h3>
                    <div className="flex items-center gap-4 text-sm text-gray-600">
                      <span>{paper.conference} {paper.year}</span>
                      <span>•</span>
                      <span className="flex items-center gap-1">
                        <TrendingUp className="w-3.5 h-3.5" />
                        {paper.citations} citations
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Sidebar */}
          <div className="space-y-6">
            <div className="bg-white rounded-2xl p-6 shadow-sm border border-gray-200">
              <h3 className="font-bold text-gray-900 mb-4">Quick Actions</h3>
              <div className="space-y-2">
                <button className="w-full flex items-center justify-between px-4 py-3 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 transition-colors">
                  <span>Search Papers</span>
                  <FileText className="w-4 h-4" />
                </button>
                <button className="w-full flex items-center justify-between px-4 py-3 bg-gray-100 text-gray-700 rounded-xl hover:bg-gray-200 transition-colors">
                  <span>Export Data</span>
                  <ExternalLink className="w-4 h-4" />
                </button>
                <button className="w-full flex items-center justify-between px-4 py-3 bg-gray-100 text-gray-700 rounded-xl hover:bg-gray-200 transition-colors">
                  <span>Share</span>
                  <Globe className="w-4 h-4" />
                </button>
              </div>
            </div>

            {type === 'author' && (
              <div className="bg-gradient-to-br from-indigo-600 to-purple-600 text-white rounded-2xl p-6 shadow-sm">
                <h3 className="font-bold mb-4">Contact Information</h3>
                <div className="space-y-3 text-sm">
                  <div className="flex items-center gap-2">
                    <Mail className="w-4 h-4" />
                    <span>{data.toLowerCase().replace(' ', '.')}@university.edu</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Globe className="w-4 h-4" />
                    <span>https://researcher-website.com</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Building className="w-4 h-4" />
                    <span>Stanford University</span>
                  </div>
                </div>
              </div>
            )}

            {type === 'conference' && (
              <div className="bg-gradient-to-br from-purple-600 to-pink-600 text-white rounded-2xl p-6 shadow-sm">
                <h3 className="font-bold mb-4">Upcoming Event</h3>
                <div className="space-y-3 text-sm">
                  <div className="flex items-center gap-2">
                    <Calendar className="w-4 h-4" />
                    <span>June 17-21, 2026</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Building className="w-4 h-4" />
                    <span>Seattle, WA, USA</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Globe className="w-4 h-4" />
                    <span>cvpr2026.thecvf.com</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
