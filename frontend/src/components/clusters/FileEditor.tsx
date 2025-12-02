/**
 * Componente de editor de arquivo
 */

import { useState, useEffect, useCallback, useMemo } from "react";
import dynamic from "next/dynamic";
import { AlertCircle, Save, X, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { webDavService } from "@/services/webdav.service";
import { githubDark } from "@uiw/codemirror-theme-github";
import { javascript } from "@codemirror/lang-javascript";
import { json } from "@codemirror/lang-json";
import { html } from "@codemirror/lang-html";
import { css } from "@codemirror/lang-css";
import { yaml } from "@codemirror/lang-yaml";
import { markdown } from "@codemirror/lang-markdown";
import { python } from "@codemirror/lang-python";
import { php } from "@codemirror/lang-php";

const CodeMirror = dynamic(() => import("@uiw/react-codemirror").then(mod => mod.default), {
  ssr: false,
});

interface FileEditorProps {
  filePath: string;
  fileName: string;
  isOpen: boolean;
  onClose: () => void;
  onSave?: () => void;
}

export function FileEditor({ filePath, fileName, isOpen, onClose, onSave }: FileEditorProps) {
  const [content, setContent] = useState<string>("");
  const [originalContent, setOriginalContent] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadFile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const fileContent = await webDavService.readFileAsText(filePath);
      setContent(fileContent);
      setOriginalContent(fileContent);
    } catch (err: any) {
      setError(`Erro ao carregar arquivo: ${err.message}`);
      setContent("");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filePath]);

  const hasChanges = content !== originalContent;

  const handleSave = useCallback(async () => {
    if (content === originalContent) {
      onClose();
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await webDavService.saveFileAsText(filePath, content);
      setOriginalContent(content);
      onSave?.();
      onClose();
    } catch (err: any) {
      setError(`Erro ao salvar arquivo: ${err.message}`);
    } finally {
      setSaving(false);
    }
  }, [content, originalContent, filePath, onClose, onSave]);

  // Carregar conteúdo do arquivo quando abrir
  useEffect(() => {
    if (isOpen && filePath) {
      loadFile();
    } else {
      // Limpar conteúdo quando fechar
      setContent("");
      setOriginalContent("");
      setError(null);
    }
  }, [isOpen, filePath, loadFile]);

  // Atalho de teclado Ctrl+S para salvar
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        if (hasChanges && !saving && !loading) {
          handleSave();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, hasChanges, saving, loading, handleSave]);

  if (!isOpen) return null;

  // Detectar extensão do arquivo para syntax highlighting básico
  const extension = fileName.split(".").pop()?.toLowerCase() || "";
  const isTextFile = !["jpg", "jpeg", "png", "gif", "webp", "pdf", "zip", "tar", "gz"].includes(extension);

  const editorExtensions = useMemo(() => {
    if (!extension) return [];

    if (["js", "jsx", "ts", "tsx"].includes(extension)) {
      return [javascript({ jsx: true, typescript: true })];
    }
    if (["json"].includes(extension)) {
      return [json()];
    }
    if (["html"].includes(extension)) {
      return [html()];
    }
    if (["css"].includes(extension)) {
      return [css()];
    }
    if (["yml", "yaml"].includes(extension)) {
      return [yaml()];
    }
    if (["md", "markdown"].includes(extension)) {
      return [markdown()];
    }
    if (["py"].includes(extension)) {
      return [python()];
    }
    if (["php"].includes(extension)) {
      return [php()];
    }
    return [];
  }, [extension]);

  const lineCount = useMemo(() => {
    if (!content) return 0;
    return content.split(/\r?\n/).length;
  }, [content]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-background border rounded-lg shadow-lg w-[90vw] h-[90vh] max-w-6xl flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">Editor de Arquivo</h2>
            <span className="text-sm text-muted-foreground">{fileName}</span>
            {hasChanges && (
              <span className="text-xs text-orange-500">(não salvo)</span>
            )}
          </div>
          <div className="flex items-center gap-2">
            {hasChanges && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleSave}
                disabled={saving || loading}
              >
                {saving ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Salvando...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4 mr-2" />
                    Salvar
                  </>
                )}
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              onClick={onClose}
              disabled={saving}
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Error */}
        {error && (
          <div className="p-3 bg-red-50 dark:bg-red-900/20 border-b border-red-200 dark:border-red-800">
            <p className="text-sm text-red-600 dark:text-red-400 flex items-center gap-2">
              <AlertCircle className="h-4 w-4" />
              {error}
            </p>
          </div>
        )}

        {/* Editor */}
        <div className="flex-1 overflow-hidden">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : !isTextFile ? (
            <div className="flex items-center justify-center h-full p-8">
              <div className="text-center">
                <AlertCircle className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
                <p className="text-muted-foreground">
                  Este tipo de arquivo não pode ser editado como texto.
                </p>
                <p className="text-sm text-muted-foreground mt-2">
                  Apenas arquivos de texto podem ser editados.
                </p>
              </div>
            </div>
          ) : (
            <ScrollArea className="h-full">
              <div className="min-h-full">
                <CodeMirror
                  value={content}
                  height="100%"
                  theme={githubDark}
                  extensions={editorExtensions}
                  basicSetup={{
                    lineNumbers: true,
                    highlightActiveLine: true,
                    highlightActiveLineGutter: true,
                  }}
                  onChange={(value) => setContent(value)}
                  editable={!saving}
                  aria-label="Editor de código"
                />
              </div>
            </ScrollArea>
          )}
        </div>

        {/* Footer */}
        <div className="p-3 border-t bg-muted/50 flex items-center justify-between text-xs text-muted-foreground">
          <div className="flex items-center gap-4">
            <span>Caminho: {filePath}</span>
            {hasChanges && (
              <span className="text-orange-500">● Alterações não salvas</span>
            )}
          </div>
          <div className="flex items-center gap-4">
            <span>{lineCount} linha{lineCount === 1 ? "" : "s"}</span>
            <div className="flex items-center gap-2">
              <kbd className="px-2 py-1 bg-background border rounded text-xs">Ctrl+S</kbd>
              <span>para salvar</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

