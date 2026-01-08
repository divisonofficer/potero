import { useState, useRef, useEffect } from 'react';
import { Camera, X } from 'lucide-react';
import { domToPng } from 'modern-screenshot';
import { toast } from 'sonner@2.0.3';

interface ScreenshotCaptureProps {
  targetRef: React.RefObject<HTMLElement>;
  buttonClassName?: string;
}

interface SelectionArea {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export function ScreenshotCapture({ targetRef, buttonClassName }: ScreenshotCaptureProps) {
  const [isCapturing, setIsCapturing] = useState(false);
  const [selection, setSelection] = useState<SelectionArea | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [overlayBounds, setOverlayBounds] = useState({ top: 0, left: 0, width: 0, height: 0 });
  const overlayRef = useRef<HTMLDivElement>(null);

  const startCapture = () => {
    if (!targetRef.current) return;
    
    // Get the bounds of the target element
    const rect = targetRef.current.getBoundingClientRect();
    setOverlayBounds({
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height,
    });
    
    setIsCapturing(true);
    setSelection(null);
    setIsDragging(false);
  };

  const cancelCapture = () => {
    setIsCapturing(false);
    setSelection(null);
    setIsDragging(false);
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if (!isCapturing || !overlayRef.current) return;
    
    const rect = overlayRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    setSelection({
      startX: x,
      startY: y,
      endX: x,
      endY: y,
    });
    setIsDragging(true);
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging || !selection || !overlayRef.current) return;
    
    const rect = overlayRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    setSelection({
      ...selection,
      endX: x,
      endY: y,
    });
  };

  const handleMouseUp = async () => {
    if (!isDragging || !selection) return;
    
    setIsDragging(false);
    
    // Calculate actual selection bounds
    const left = Math.min(selection.startX, selection.endX);
    const top = Math.min(selection.startY, selection.endY);
    const width = Math.abs(selection.endX - selection.startX);
    const height = Math.abs(selection.endY - selection.startY);
    
    // Minimum size check
    if (width < 10 || height < 10) {
      toast.error('Selection too small');
      cancelCapture();
      return;
    }
    
    // Capture the screenshot
    await captureArea(left, top, width, height);
  };

  const captureArea = async (left: number, top: number, width: number, height: number) => {
    if (!targetRef.current) return;
    
    try {
      // Show loading toast
      const loadingToast = toast.loading('Capturing screenshot...');
      
      // Capture the entire element as PNG data URL
      const dataUrl = await domToPng(targetRef.current, {
        scale: 2,
        quality: 0.95,
        backgroundColor: '#ffffff',
      });
      
      // Load the image
      const img = new Image();
      await new Promise<void>((resolve, reject) => {
        img.onload = () => resolve();
        img.onerror = reject;
        img.src = dataUrl;
      });
      
      // Create a canvas for cropping
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        toast.dismiss(loadingToast);
        toast.error('Failed to create canvas');
        return;
      }
      
      // Set canvas size to the selection (accounting for 2x scale)
      canvas.width = width * 2;
      canvas.height = height * 2;
      
      // Draw the cropped portion
      ctx.drawImage(
        img,
        left * 2, // Source x
        top * 2, // Source y
        width * 2, // Source width
        height * 2, // Source height
        0, // Dest x
        0, // Dest y
        width * 2, // Dest width
        height * 2 // Dest height
      );
      
      // Convert to blob
      const blob = await new Promise<Blob | null>((resolve) => {
        canvas.toBlob(resolve, 'image/png', 1.0);
      });
      
      if (!blob) {
        toast.dismiss(loadingToast);
        toast.error('Failed to create image');
        return;
      }
      
      // Try to copy to clipboard
      try {
        await navigator.clipboard.write([
          new ClipboardItem({
            'image/png': blob,
          }),
        ]);
        toast.dismiss(loadingToast);
        toast.success('Screenshot copied to clipboard!', {
          description: `${Math.round(width)} × ${Math.round(height)} px`,
        });
      } catch (err) {
        console.error('Failed to copy to clipboard:', err);
        
        // Fallback: download the image
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `screenshot-${Date.now()}.png`;
        a.click();
        URL.revokeObjectURL(url);
        
        toast.dismiss(loadingToast);
        toast.info('Screenshot downloaded', {
          description: 'Clipboard access denied, file downloaded instead',
        });
      }
      
    } catch (err) {
      console.error('Screenshot failed:', err);
      toast.error('Failed to capture screenshot');
    } finally {
      cancelCapture();
    }
  };

  // Handle ESC key to cancel
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isCapturing) {
        cancelCapture();
      }
    };
    
    if (isCapturing) {
      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }
  }, [isCapturing]);

  // Calculate selection rectangle for display
  const getSelectionStyle = () => {
    if (!selection) return {};
    
    const left = Math.min(selection.startX, selection.endX);
    const top = Math.min(selection.startY, selection.endY);
    const width = Math.abs(selection.endX - selection.startX);
    const height = Math.abs(selection.endY - selection.startY);
    
    return {
      left: `${left}px`,
      top: `${top}px`,
      width: `${width}px`,
      height: `${height}px`,
    };
  };

  return (
    <>
      {/* Screenshot Button */}
      <button
        onClick={startCapture}
        className={buttonClassName || "p-2 rounded-lg hover:bg-gray-100 transition-colors"}
        title="Take Screenshot"
      >
        <Camera className="w-4 h-4" />
      </button>

      {/* Capture Overlay */}
      {isCapturing && (
        <div
          ref={overlayRef}
          className="fixed z-[10000] cursor-crosshair"
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          style={{
            background: 'rgba(0, 0, 0, 0.3)',
            top: `${overlayBounds.top}px`,
            left: `${overlayBounds.left}px`,
            width: `${overlayBounds.width}px`,
            height: `${overlayBounds.height}px`,
          }}
        >
          {/* Instructions */}
          <div className="absolute top-4 left-1/2 -translate-x-1/2 bg-black/80 text-white px-4 py-2 rounded-lg text-sm backdrop-blur-sm">
            Drag to select area • Press ESC to cancel
          </div>

          {/* Cancel Button */}
          <button
            onClick={cancelCapture}
            className="absolute top-4 right-4 p-2 bg-black/80 text-white rounded-lg hover:bg-black/90 transition-colors backdrop-blur-sm"
          >
            <X className="w-5 h-5" />
          </button>

          {/* Selection Rectangle */}
          {selection && (
            <div
              className="absolute border-2 border-indigo-500 bg-indigo-500/10 pointer-events-none"
              style={getSelectionStyle()}
            >
              {/* Corner Handles */}
              <div className="absolute -top-1 -left-1 w-2 h-2 bg-indigo-500 rounded-full" />
              <div className="absolute -top-1 -right-1 w-2 h-2 bg-indigo-500 rounded-full" />
              <div className="absolute -bottom-1 -left-1 w-2 h-2 bg-indigo-500 rounded-full" />
              <div className="absolute -bottom-1 -right-1 w-2 h-2 bg-indigo-500 rounded-full" />
              
              {/* Size Label */}
              <div className="absolute -top-8 left-0 bg-indigo-600 text-white px-2 py-1 rounded text-xs whitespace-nowrap">
                {Math.round(Math.abs(selection.endX - selection.startX))} × {Math.round(Math.abs(selection.endY - selection.startY))} px
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
}