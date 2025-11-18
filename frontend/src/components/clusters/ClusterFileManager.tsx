import { ReactNode, useMemo, useState, useEffect, useCallback, useRef } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import {
  ArrowLeft,
  ArrowRight,
  ChevronsUpDown,
  ChevronRight,
  Cloud,
  Columns3,
  Folder,
  FolderKanban,
  FolderPlus,
  HardDrive,
  List,
  RefreshCw,
  Search,
  Upload,
  FileText,
  File,
  Loader2,
  AlertCircle,
  Edit
} from "lucide-react";
import { webDavService, WebDavFile } from "@/services/webdav.service";
import { ClusterAccessInfo } from "@/types";
import { FileEditor } from "./FileEditor";

interface FileNode {
  id: string;
  name: string;
  type: "folder" | "file";
  size?: string;
  modifiedAt: string;
  path: string;
  children?: FileNode[];
  extension?: string;
}

interface ClusterFileManagerProps {
  clusterName: string;
  clusterId: string;
  webDavCredentials?: ClusterAccessInfo;
  endpointHint?: string;
}


// Função auxiliar para formatar tamanho de arquivo
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + " " + sizes[i];
};

const extensionIcon = (extension?: string) => {
  if (!extension) return <File className="h-4 w-4 text-blue-500" />;
  if (["yml", "yaml", "json", "env"].includes(extension)) {
    return <FileText className="h-4 w-4 text-rose-500" />;
  }
  if (["html", "css", "js", "ts", "tsx"].includes(extension)) {
    return <FileText className="h-4 w-4 text-amber-500" />;
  }
  if (["docker"].includes(extension)) {
    return <Cloud className="h-4 w-4 text-sky-500" />;
  }
  return <File className="h-4 w-4 text-blue-500" />;
};

export function ClusterFileManager({ clusterName, clusterId, webDavCredentials, endpointHint }: ClusterFileManagerProps) {
  // O WebDAV monta o volume em /media dentro do container
  // Por isso começamos em /media ao invés de /
  const [currentPath, setCurrentPath] = useState<string>("/media");
  const [files, setFiles] = useState<WebDavFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);
  const [viewMode, setViewMode] = useState<"list" | "grid">("list");
  const [searchTerm, setSearchTerm] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [editingFile, setEditingFile] = useState<{ path: string; name: string } | null>(null);

  // Carregar diretório
  const loadDirectory = useCallback(async (path: string) => {
    if (!webDavService.isConnected()) {
      setError("WebDAV não está conectado");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const items = await webDavService.listDirectory(path);
      // Filtrar o próprio diretório (se aparecer na lista)
      const filteredItems = items.filter(item => item.filename !== path || path === "/");
      setFiles(filteredItems);
      setCurrentPath(path);
    } catch (err: any) {
      setError(`Erro ao carregar diretório: ${err.message}`);
      setFiles([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Conectar ao WebDAV quando as credenciais estiverem disponíveis
  useEffect(() => {
    if (webDavCredentials?.username && webDavCredentials?.password && webDavCredentials?.port) {
      try {
        webDavService.connect({
          clusterId,
          port: webDavCredentials.port,
          username: webDavCredentials.username,
          password: webDavCredentials.password,
        });
        setConnected(true);
        loadDirectory(currentPath);
      } catch (err: any) {
        setError(`Erro ao conectar ao WebDAV: ${err.message}`);
        setConnected(false);
      }
    } else {
      setError("Credenciais WebDAV não disponíveis");
      setConnected(false);
    }

    return () => {
      webDavService.disconnect();
    };
  }, [clusterId, webDavCredentials, loadDirectory, currentPath]);

  // Navegar para uma pasta
  const navigateToFolder = useCallback((path: string) => {
    loadDirectory(path);
  }, [loadDirectory]);

  // Navegar para pasta pai
  const navigateUp = useCallback(() => {
    // Não permitir navegar acima de /media
    if (currentPath === "/media" || currentPath === "/") return;
    const parentPath = currentPath.split("/").slice(0, -1).join("/") || "/media";
    // Garantir que não vá abaixo de /media
    if (!parentPath.startsWith("/media")) {
      loadDirectory("/media");
    } else {
      loadDirectory(parentPath);
    }
  }, [currentPath, loadDirectory]);

  // Criar pasta
  const handleCreateFolder = useCallback(async () => {
    const folderName = prompt("Nome da pasta:");
    if (!folderName || !folderName.trim()) return;

    // Garantir que o caminho comece com /media
    const basePath = currentPath.startsWith("/media") ? currentPath : "/media";
    const newPath = basePath === "/media" 
      ? `/media/${folderName.trim()}`
      : `${basePath}/${folderName.trim()}`;

    try {
      await webDavService.createDirectory(newPath);
      loadDirectory(currentPath); // Recarregar lista
    } catch (err: any) {
      setError(`Erro ao criar pasta: ${err.message}`);
    }
  }, [currentPath, loadDirectory]);

  // Upload de arquivo
  const handleUpload = useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    // Garantir que o caminho comece com /media
    const basePath = currentPath.startsWith("/media") ? currentPath : "/media";
    const remotePath = basePath === "/media" 
      ? `/media/${file.name}`
      : `${basePath}/${file.name}`;

    try {
      await webDavService.uploadFile(remotePath, file);
      loadDirectory(currentPath); // Recarregar lista
    } catch (err: any) {
      setError(`Erro ao fazer upload: ${err.message}`);
    } finally {
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  }, [currentPath, loadDirectory]);

  // Converter WebDavFile para FileNode
  const fileNodes: FileNode[] = useMemo(() => {
    return files.map((file, index) => {
      const extension = file.basename.includes(".") 
        ? file.basename.split(".").pop()?.toLowerCase() 
        : undefined;
      
      return {
        id: file.filename,
        name: file.basename,
        type: file.type,
        size: file.type === "file" ? formatFileSize(file.size) : undefined,
        modifiedAt: file.lastmod ? new Date(file.lastmod).toLocaleString("pt-BR") : "",
        path: file.filename,
        extension,
      };
    });
  }, [files]);

  // Filtrar arquivos pela busca
  const filteredFiles = useMemo(() => {
    if (!searchTerm) return fileNodes;
    return fileNodes.filter(file => 
      file.name.toLowerCase().includes(searchTerm.toLowerCase())
    );
  }, [fileNodes, searchTerm]);

  // Construir breadcrumb
  const breadcrumb = useMemo(() => {
    // Garantir que o caminho comece com /media
    const path = currentPath.startsWith("/media") ? currentPath : "/media";
    
    // Se o caminho é exatamente /media, retornar apenas o item raiz
    if (path === "/media") {
      return [
        { id: "/media", name: "Raiz", type: "folder", modifiedAt: "", path: "/media" }
      ];
    }
    
    const parts = path.split("/").filter(Boolean);
    const items: FileNode[] = [
      { id: "/media", name: "Raiz", type: "folder", modifiedAt: "", path: "/media" }
    ];
    
    // Construir caminho incrementalmente, começando de /media
    let current = "/media";
    parts.forEach((part, index) => {
      // Pular "media" se for o primeiro item (já está na raiz)
      if (index === 0 && part === "media") {
        return;
      }
      current += `/${part}`;
      items.push({
        id: current,
        name: part,
        type: "folder",
        modifiedAt: "",
        path: current,
      });
    });

    return items;
  }, [currentPath]);

  const statusText = `${filteredFiles.length} item${filteredFiles.length === 1 ? "" : "s"}`;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Badge variant="secondary" className="uppercase tracking-wide">
            WebDAV
          </Badge>
          <span className="text-sm text-muted-foreground">
            Montado como `W:` – pronto para sincronizar arquivos do cluster
          </span>
        </div>
        <div className="flex items-center gap-2">
          <Button 
            variant="outline" 
            size="sm"
            onClick={() => loadDirectory(currentPath)}
            disabled={loading || !connected}
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? "animate-spin" : ""}`} />
            {loading ? "Carregando..." : "Atualizar"}
          </Button>
          {connected ? (
            <Badge variant="default" className="bg-green-500">
              <Cloud className="h-3 w-3 mr-1" />
              Conectado
            </Badge>
          ) : (
            <Badge variant="destructive">
              <AlertCircle className="h-3 w-3 mr-1" />
              Desconectado
            </Badge>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div className="flex items-center gap-1">
          <Button 
            variant="outline" 
            size="icon"
            onClick={navigateUp}
            disabled={currentPath === "/media" || currentPath === "/" || loading}
            title="Voltar"
          >
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" disabled title="Avançar">
            <ArrowRight className="h-4 w-4" />
          </Button>
        </div>
        <div className="flex-1 flex items-center gap-2 bg-muted rounded-lg px-2 py-1 border">
          <HardDrive className="h-4 w-4 text-muted-foreground" />
          <div className="flex items-center flex-wrap gap-1 text-sm">
            {breadcrumb.map((node, index) => (
              <div key={node.id} className="flex items-center gap-1">
                <button
                  className="hover:underline"
                  onClick={() => node.type === "folder" && navigateToFolder(node.path)}
                  disabled={loading}
                >
                  {node.name}
                </button>
                {index < breadcrumb.length - 1 && <ChevronRight className="h-4 w-4 text-muted-foreground" />}
              </div>
            ))}
          </div>
        </div>
        <div className="w-64 relative">
          <Input
            placeholder="Pesquisar arquivos..."
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            className="pr-10"
          />
          <Search className="h-4 w-4 text-muted-foreground absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>
      </div>

      <div className="flex justify-between items-center bg-muted rounded-lg px-3 py-2 border">
        <div className="flex items-center gap-2">
          <Button 
            variant="ghost" 
            size="sm"
            onClick={handleCreateFolder}
            disabled={!connected || loading}
          >
            <FolderPlus className="h-4 w-4 mr-2" />
            Nova pasta
          </Button>
          <Button 
            variant="ghost" 
            size="sm"
            onClick={() => fileInputRef.current?.click()}
            disabled={!connected || loading}
          >
            <Upload className="h-4 w-4 mr-2" />
            Upload
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            onChange={handleUpload}
          />
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant={viewMode === "list" ? "default" : "ghost"}
            size="icon"
            onClick={() => setViewMode("list")}
          >
            <List className="h-4 w-4" />
          </Button>
          <Button
            variant={viewMode === "grid" ? "default" : "ghost"}
            size="icon"
            onClick={() => setViewMode("grid")}
          >
            <Columns3 className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
          <p className="text-sm text-red-600 dark:text-red-400 flex items-center gap-2">
            <AlertCircle className="h-4 w-4" />
            {error}
          </p>
        </div>
      )}

      <div className="grid grid-cols-12 gap-4">
        <CardLikePanel 
          title={`Conteúdo de ${currentPath === "/" ? "Raiz" : currentPath.split("/").pop()}`}
          className="col-span-12 min-h-[320px]"
        >
          {viewMode === "list" ? (
            <div className="rounded-lg border bg-background">
              <div className="grid grid-cols-12 px-4 py-2 border-b text-xs text-muted-foreground">
                <span className="col-span-5">Nome</span>
                <span className="col-span-3">Tipo</span>
                <span className="col-span-2">Modificado em</span>
                <span className="col-span-2 text-right">Tamanho</span>
              </div>
              <ScrollArea className="h-[240px]">
                {loading ? (
                  <div className="flex items-center justify-center h-full">
                    <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                  </div>
                ) : filteredFiles.length === 0 ? (
                  <div className="text-sm text-muted-foreground px-4 py-6">
                    {searchTerm 
                      ? "Nenhum item encontrado com esse termo."
                      : "Diretório vazio. Utilize o botão Upload ou crie uma nova pasta."}
                  </div>
                ) : (
                  filteredFiles.map((item) => (
                    <div
                      key={item.id}
                      className="grid grid-cols-12 items-center px-4 py-2 text-sm hover:bg-muted/70 group"
                    >
                      <button
                        className="col-span-5 flex items-center gap-2 text-left"
                        onClick={() => item.type === "folder" && navigateToFolder(item.path)}
                        disabled={loading}
                      >
                        {item.type === "folder" ? (
                          <Folder className="h-4 w-4 text-primary" />
                        ) : (
                          extensionIcon(item.extension)
                        )}
                        {item.name}
                      </button>
                      <span className="col-span-3">{item.type === "folder" ? "Pasta" : item.extension?.toUpperCase() || "Arquivo"}</span>
                      <span className="col-span-2">{item.modifiedAt || "-"}</span>
                      <div className="col-span-2 flex items-center justify-end gap-2">
                        <span className="text-right">{item.type === "folder" ? "-" : item.size ?? "-"}</span>
                        {item.type === "file" && (
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                            onClick={(e) => {
                              e.stopPropagation();
                              // Usar o caminho completo do arquivo
                              let filePath = item.path;
                              // Garantir que comece com /media
                              if (!filePath.startsWith("/media")) {
                                filePath = currentPath === "/media" 
                                  ? `/media/${item.name}`
                                  : `${currentPath}/${item.name}`;
                              }
                              setEditingFile({ path: filePath, name: item.name });
                            }}
                            title="Editar arquivo"
                          >
                            <Edit className="h-3 w-3" />
                          </Button>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </ScrollArea>
            </div>
          ) : (
            <ScrollArea className="h-[280px]">
              {loading ? (
                <div className="flex items-center justify-center h-full">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : (
                <div className="grid grid-cols-3 gap-4 pr-2">
                  {filteredFiles.map((item) => (
                    <div
                      key={item.id}
                      className="border rounded-lg p-3 text-left hover:border-primary relative group"
                    >
                      <button
                        className="w-full"
                        onClick={() => item.type === "folder" && navigateToFolder(item.path)}
                        disabled={loading}
                      >
                        <div className="flex items-center gap-3">
                          {item.type === "folder" ? (
                            <Folder className="h-10 w-10 text-primary" />
                          ) : (
                            extensionIcon(item.extension)
                          )}
                          <div className="flex-1">
                            <p className="font-medium">{item.name}</p>
                            <p className="text-xs text-muted-foreground">
                              {item.type === "folder" ? "Pasta" : `${item.extension?.toUpperCase() || "Arquivo"} • ${item.size || "-"}`}
                            </p>
                          </div>
                        </div>
                      </button>
                      {item.type === "file" && (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="absolute top-2 right-2 h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                          onClick={(e) => {
                            e.stopPropagation();
                            const filePath = item.path.startsWith("/media") ? item.path : `/media/${item.name}`;
                            setEditingFile({ path: filePath, name: item.name });
                          }}
                          title="Editar arquivo"
                        >
                          <Edit className="h-3 w-3" />
                        </Button>
                      )}
                    </div>
                  ))}
                  {filteredFiles.length === 0 && (
                    <div className="text-sm text-muted-foreground px-4 py-6 col-span-3">
                      {searchTerm 
                        ? "Nenhum item encontrado com esse termo."
                        : "Diretório vazio. Utilize o botão Upload ou crie uma nova pasta."}
                    </div>
                  )}
                </div>
              )}
            </ScrollArea>
          )}
        </CardLikePanel>
      </div>

      <div className="flex items-center justify-between text-xs text-muted-foreground border rounded-md px-3 py-2 bg-muted/50">
        <span>{statusText}</span>
        <span>
          Conectado como <strong>{clusterName}</strong> via {endpointHint ?? "webdav://clusterforge.local"}
        </span>
      </div>

      {/* Editor de Arquivo */}
      {editingFile && (
        <FileEditor
          filePath={editingFile.path}
          fileName={editingFile.name}
          isOpen={!!editingFile}
          onClose={() => setEditingFile(null)}
          onSave={() => {
            // Recarregar diretório após salvar
            loadDirectory(currentPath);
          }}
        />
      )}
    </div>
  );
}

function CardLikePanel({ title, className, children }: { title: string; className?: string; children: ReactNode }) {
  return (
    <div className={`border rounded-lg bg-background shadow-sm ${className ?? ""}`}>
      <div className="border-b px-3 py-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {title}
      </div>
      <div className="p-3">{children}</div>
    </div>
  );
}



