import { useMemo, useState, useEffect, useCallback, useRef } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
// import { Separator } from "@/components/ui/separator";
import {
  ArrowLeft,
  ArrowRight,
  // ChevronsUpDown,
  ChevronRight,
  Cloud,
  Columns3,
  FilePlus,
  Folder,
  // FolderKanban,
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

const ROOT_PATH = "/";

const normalizePath = (path?: string): string => {
  if (!path || path === ROOT_PATH) {
    return ROOT_PATH;
  }
  let normalized = path.trim();
  if (!normalized.startsWith(ROOT_PATH)) {
    normalized = `${ROOT_PATH}${normalized}`;
  }
  normalized = normalized.replace(/\/{2,}/g, "/");
  if (normalized.length > 1 && normalized.endsWith("/")) {
    normalized = normalized.slice(0, -1);
  }
  return normalized || ROOT_PATH;
};

const joinPath = (base: string, segment: string): string => {
  const cleanSegment = segment.replace(/^\/+/, "").trim();
  if (!cleanSegment) {
    return normalizePath(base);
  }
  const cleanBase = base === ROOT_PATH ? "" : normalizePath(base).slice(1);
  const combined = [cleanBase, cleanSegment].filter(Boolean).join("/");
  return normalizePath(`${ROOT_PATH}${combined}`);
};

interface FileNode {
  id: string;
  name: string;
  type: "folder" | "file";
  size?: string;
  modifiedAt: string;
  path: string;
  children?: FileNode[];
  extension?: string;
  rawSize?: number;
  rawModified?: number;
}

type SortableFileNodeKey = Exclude<keyof FileNode, "children">;





interface ClusterFileManagerProps {
  clusterId: string;
  webDavCredentials?: ClusterAccessInfo;
  endpointHint?: string;
}



const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + " " + sizes[i];
};

const extensionIcon = (extension?: string, className: string = "h-4 w-4") => {
  if (!extension) return <File className={`${className} text-blue-500`} />;
  if (["yml", "yaml", "json", "env"].includes(extension)) {
    return <FileText className={`${className} text-rose-500`} />;
  }
  if (["html", "css", "js", "ts", "tsx"].includes(extension)) {
    return <FileText className={`${className} text-amber-500`} />;
  }
  if (["docker"].includes(extension)) {
    return <Cloud className={`${className} text-sky-500`} />;
  }
  return <File className={`${className} text-blue-500`} />;
};

export function ClusterFileManager({ clusterId, webDavCredentials, endpointHint }: ClusterFileManagerProps) {
  const [currentPath, setCurrentPath] = useState<string>(ROOT_PATH);
  const [files, setFiles] = useState<WebDavFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);
  const [viewMode, setViewMode] = useState<"list" | "grid">("list");
  const [searchTerm, setSearchTerm] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [editingFile, setEditingFile] = useState<{ path: string; name: string } | null>(null);


  const [sortConfig, setSortConfig] = useState<{ key: SortableFileNodeKey; direction: "asc" | "desc" } | null>(null);

  const loadDirectory = useCallback(async (path: string) => {
    if (!webDavService.isConnected()) {
      setError("WebDAV não está conectado");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const normalizedPath = normalizePath(path);
      const items = await webDavService.listDirectory(normalizedPath);

      const filteredItems = items.filter(item => item.filename !== normalizedPath || normalizedPath === ROOT_PATH);
      setFiles(filteredItems);
      setCurrentPath(normalizedPath);
    } catch (err: unknown) {
      const error = err as Error;
      setError(`Erro ao carregar diretório: ${error.message}`);
      setFiles([]);
    } finally {
      setLoading(false);
    }
  }, []);


  useEffect(() => {
    if (webDavCredentials?.username && webDavCredentials?.password && webDavCredentials?.port) {
      try {
        webDavService.connect({
          clusterId,
          port: webDavCredentials.port,
          username: webDavCredentials.username,
          password: webDavCredentials.password,
        });
        setCurrentPath(ROOT_PATH);
        setConnected(true);
        loadDirectory(ROOT_PATH);
      } catch (err: unknown) {
        const error = err as Error;
        setError(`Erro ao conectar ao WebDAV: ${error.message}`);
        setConnected(false);
      }
    } else {
      setError("Credenciais WebDAV não disponíveis");
      setConnected(false);
    }

    return () => {
      webDavService.disconnect();
    };
  }, [clusterId, webDavCredentials, loadDirectory]);


  const navigateToFolder = useCallback((path: string) => {
    loadDirectory(path);
  }, [loadDirectory]);


  const navigateUp = useCallback(() => {
    if (currentPath === ROOT_PATH) return;
    const parentPath = currentPath.split("/").slice(0, -1).join("/") || ROOT_PATH;
    loadDirectory(parentPath || ROOT_PATH);
  }, [currentPath, loadDirectory]);


  const handleCreateFolder = useCallback(async () => {
    const folderName = prompt("Nome da pasta:");
    if (!folderName || !folderName.trim()) return;

    const newPath = joinPath(currentPath, folderName.trim());
    try {
      await webDavService.createDirectory(newPath);
      loadDirectory(currentPath);
    } catch (err: unknown) {
      const error = err as Error;
      setError(`Erro ao criar pasta: ${error.message}`);
    }
  }, [currentPath, loadDirectory]);


  const handleCreateFile = useCallback(async () => {
    const fileName = prompt("Nome do arquivo (ex: config.yaml):");
    if (!fileName || !fileName.trim()) return;

    const newPath = normalizePath(joinPath(currentPath, fileName.trim()));
    try {
      await webDavService.saveFileAsText(newPath, "");
      loadDirectory(currentPath);
      setEditingFile({ path: newPath, name: fileName.trim() });
    } catch (err: unknown) {
      const error = err as Error;
      setError(`Erro ao criar arquivo: ${error.message}`);
    }
  }, [currentPath, loadDirectory]);


  const handleUpload = useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const remotePath = joinPath(currentPath, file.name);

    try {
      await webDavService.uploadFile(remotePath, file);
      loadDirectory(currentPath);
    } catch (err: unknown) {
      const error = err as Error;
      setError(`Erro ao fazer upload: ${error.message}`);
    } finally {
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  }, [currentPath, loadDirectory]);


  const fileNodes: FileNode[] = useMemo(() => {
    return files.map((file) => {
      const extension = file.basename.includes(".")
        ? file.basename.split(".").pop()?.toLowerCase()
        : undefined;

      return {
        id: file.filename,
        name: file.basename,
        type: file.type === "directory" ? "folder" : "file",
        size: file.type === "file" ? formatFileSize(file.size) : undefined,
        modifiedAt: file.lastmod ? new Date(file.lastmod).toLocaleString("pt-BR") : "",
        path: normalizePath(file.filename),
        extension,
        rawSize: file.size,
        rawModified: file.lastmod ? new Date(file.lastmod).getTime() : 0,
      };
    });
  }, [files]);


  const filteredFiles = useMemo(() => {
    let result = [...fileNodes];

    if (searchTerm) {
      result = result.filter(file =>
        file.name.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    if (sortConfig) {
      result.sort((a, b) => {
        // Always put folders first if not sorting by explicit fields that might act differently,
        // but typically standard OS behavior is folders first. Let's keep folders first for Name.
        if (sortConfig.key === 'name') {
          if (a.type !== b.type) {
            return a.type === 'folder' ? -1 : 1;
          }
        }

        let aValue: string | number | undefined = a[sortConfig.key];
        let bValue: string | number | undefined = b[sortConfig.key];

        // Custom sorting for specific fields
        if (sortConfig.key === 'size') {
          // For size, use raw bytes if available, folders assume 0 or -1 to stay at top/bottom?
          // Actually, usually folders have no size. Let's treat them as smaller than any file or separate.
          // If we want folders first always:
          if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
          aValue = a.rawSize ?? 0;
          bValue = b.rawSize ?? 0;
        } else if (sortConfig.key === 'modifiedAt') {
          aValue = a.rawModified ?? 0;
          bValue = b.rawModified ?? 0;
        }

        if (aValue === undefined && bValue === undefined) return 0;
        if (aValue === undefined) return 1;
        if (bValue === undefined) return -1;

        if (aValue < bValue) {
          return sortConfig.direction === 'asc' ? -1 : 1;
        }
        if (aValue > bValue) {
          return sortConfig.direction === 'asc' ? 1 : -1;
        }
        return 0;
      });
    } else {
      // Default sort: Folders first, then files by name
      result.sort((a, b) => {
        if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
    }

    return result;
  }, [fileNodes, searchTerm, sortConfig]);


  const breadcrumb = useMemo(() => {
    const path = normalizePath(currentPath);

    if (path === ROOT_PATH) {
      return [
        { id: ROOT_PATH, name: "Raiz", type: "folder", modifiedAt: "", path: ROOT_PATH }
      ];
    }

    const parts = path.split("/").filter(Boolean);
    const items: FileNode[] = [
      { id: ROOT_PATH, name: "Raiz", type: "folder", modifiedAt: "", path: ROOT_PATH }
    ];

    let current = ROOT_PATH;
    parts.forEach((part) => {
      current = current === ROOT_PATH ? `/${part}` : `${current}/${part}`;
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

  const requestSort = (key: SortableFileNodeKey) => {
    let direction: "asc" | "desc" = "asc";
    if (sortConfig && sortConfig.key === key && sortConfig.direction === "asc") {
      direction = "desc";
    }
    setSortConfig({ key, direction });
  };

  const statusText = `${filteredFiles.length} item${filteredFiles.length === 1 ? "" : "s"}`;

  return (
    <div className="flex flex-col h-full bg-background">
      <div className="border-b px-4 py-3 flex items-center justify-between bg-muted/30">
        <div className="flex items-center gap-2">
          <Badge variant="secondary" className="uppercase tracking-wide text-xs">
            WebDAV
          </Badge>
          <span className="text-xs text-muted-foreground hidden sm:inline-block">
            Montado como `W:`
          </span>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => loadDirectory(currentPath)}
            disabled={loading || !connected}
            className="h-8"
          >
            <RefreshCw className={`h-3.5 w-3.5 mr-2 ${loading ? "animate-spin" : ""}`} />
            {loading ? "Carregando..." : "Atualizar"}
          </Button>
          {connected ? (
            <Badge variant="outline" className="text-green-600 border-green-200 bg-green-50">
              <Cloud className="h-3 w-3 mr-1" />
              Conectado
            </Badge>
          ) : (
            <Badge variant="destructive" className="text-xs">
              <AlertCircle className="h-3 w-3 mr-1" />
              Desconectado
            </Badge>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2 px-4 py-2 border-b">
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            onClick={navigateUp}
            disabled={currentPath === ROOT_PATH || loading}
            title="Voltar"
            className="h-8 w-8"
          >
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" disabled title="Avançar" className="h-8 w-8">
            <ArrowRight className="h-4 w-4" />
          </Button>
        </div>
        <div className="flex-1 flex items-center gap-2 bg-muted/50 rounded-md px-3 py-1.5 border text-sm">
          <HardDrive className="h-4 w-4 text-muted-foreground" />
          <div className="flex items-center flex-wrap gap-1">
            {breadcrumb.map((node, index) => (
              <div key={node.id} className="flex items-center gap-1">
                <button
                  className="hover:text-primary hover:underline transition-colors"
                  onClick={() => node.type === "folder" && navigateToFolder(node.path)}
                  disabled={loading}
                >
                  {node.name}
                </button>
                {index < breadcrumb.length - 1 && <ChevronRight className="h-3.5 w-3.5 text-muted-foreground/60" />}
              </div>
            ))}
          </div>
        </div>
        <div className="w-56 lg:w-72 relative">
          <Input
            placeholder="Pesquisar..."
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            className="pr-8 h-9 text-sm"
          />
          <Search className="h-4 w-4 text-muted-foreground absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>
      </div>

      <div className="px-4 py-2 border-b flex justify-between items-center bg-muted/10">
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleCreateFolder}
            disabled={!connected || loading}
            className="h-8 text-xs sm:text-sm"
          >
            <FolderPlus className="h-4 w-4 mr-2 text-blue-500" />
            Nova pasta
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleCreateFile}
            disabled={!connected || loading}
            className="h-8 text-xs sm:text-sm"
          >
            <FilePlus className="h-4 w-4 mr-2 text-green-500" />
            Novo arquivo
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => fileInputRef.current?.click()}
            disabled={!connected || loading}
            className="h-8 text-xs sm:text-sm"
          >
            <Upload className="h-4 w-4 mr-2 text-indigo-500" />
            Upload
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            onChange={handleUpload}
          />
        </div>
        <div className="flex items-center gap-1 bg-muted/50 p-0.5 rounded-md border">
          <Button
            variant={viewMode === "list" ? "secondary" : "ghost"}
            size="icon"
            onClick={() => setViewMode("list")}
            className="h-7 w-7"
          >
            <List className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant={viewMode === "grid" ? "secondary" : "ghost"}
            size="icon"
            onClick={() => setViewMode("grid")}
            className="h-7 w-7"
          >
            <Columns3 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg flex items-center justify-between mx-4 mt-4">
          <p className="text-sm text-red-600 dark:text-red-400 flex items-center gap-2">
            <AlertCircle className="h-4 w-4" />
            {error}
          </p>
          <Button variant="ghost" size="sm" onClick={() => setError(null)} className="h-6 w-6 p-0 text-red-500 hover:text-red-700 hover:bg-red-100">
            <span className="sr-only">Fechar</span>
            <span className="text-lg">×</span>
          </Button>
        </div>
      )}

      <div className="flex-1 overflow-hidden relative min-h-0">
        {viewMode === "list" ? (
          <div className="h-full flex flex-col min-h-0">
            <div className="grid grid-cols-12 px-6 py-2 border-b text-xs font-medium text-muted-foreground bg-muted/20">
              <div className="col-span-6 cursor-pointer hover:text-foreground flex items-center gap-1" onClick={() => requestSort('name')}>
                Nome {sortConfig?.key === 'name' && (sortConfig.direction === 'asc' ? '↑' : '↓')}
              </div>
              <div className="col-span-2 cursor-pointer hover:text-foreground flex items-center gap-1" onClick={() => requestSort('extension')}>
                Tipo {sortConfig?.key === 'extension' && (sortConfig.direction === 'asc' ? '↑' : '↓')}
              </div>
              <div className="col-span-2 cursor-pointer hover:text-foreground flex items-center gap-1" onClick={() => requestSort('modifiedAt')}>
                Modificado em {sortConfig?.key === 'modifiedAt' && (sortConfig.direction === 'asc' ? '↑' : '↓')}
              </div>
              <div className="col-span-2 text-right cursor-pointer hover:text-foreground flex items-center justify-end gap-1" onClick={() => requestSort('size')}>
                Tamanho {sortConfig?.key === 'size' && (sortConfig.direction === 'asc' ? '↑' : '↓')}
              </div>
            </div>
            <div className="flex-1 relative min-h-0">
              <ScrollArea className="h-full w-full absolute inset-0">
                {loading ? (
                  <div className="flex flex-col items-center justify-center p-12">
                    <Loader2 className="h-8 w-8 animate-spin text-primary mb-2" />
                    <p className="text-sm text-muted-foreground">Listando arquivos...</p>
                  </div>
                ) : filteredFiles.length === 0 ? (
                  <div className="flex flex-col items-center justify-center p-12 text-muted-foreground">
                    <Folder className="h-12 w-12 mb-4 text-muted-foreground/30" />
                    <p className="text-lg font-medium text-foreground">Diretório vazio</p>
                    <p className="text-sm">Utilize os botões acima para adicionar conteúdo.</p>
                  </div>
                ) : (
                  <div className="pb-24">
                    {filteredFiles.map((item) => (
                      <div
                        key={item.id}
                        className="grid grid-cols-12 items-center px-6 py-2.5 text-sm hover:bg-muted/50 border-b border-border/40 group transition-colors cursor-pointer"
                        onClick={() => item.type === "folder" ? navigateToFolder(item.path) : null}
                      >
                        <div className="col-span-6 flex items-center gap-3 overflow-hidden">
                          {item.type === "folder" ? (
                            <Folder className="h-5 w-5 text-blue-500 fill-blue-500/20" />
                          ) : (
                            extensionIcon(item.extension, "h-5 w-5")
                          )}
                          <span className="truncate font-medium text-foreground">{item.name}</span>
                        </div>
                        <span className="col-span-2 text-muted-foreground text-xs uppercase">{item.type === "folder" ? "Pasta" : item.extension || "Arquivo"}</span>
                        <span className="col-span-2 text-muted-foreground text-xs">{item.modifiedAt || "-"}</span>
                        <div className="col-span-2 flex items-center justify-end gap-3">
                          <span className="text-muted-foreground font-mono text-xs">{item.type === "folder" ? "-" : item.size ?? "-"}</span>
                          {item.type === "file" && (
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7 opacity-0 group-hover:opacity-100 transition-opacity"
                              onClick={(e) => {
                                e.stopPropagation();
                                const filePath = item.path ? normalizePath(item.path) : joinPath(currentPath, item.name);
                                setEditingFile({ path: filePath, name: item.name });
                              }}
                              title="Editar arquivo"
                            >
                              <Edit className="h-3.5 w-3.5" />
                            </Button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </ScrollArea>
            </div>
          </div>
        ) : (
          <div className="flex-1 relative min-h-0">
            <ScrollArea className="h-full w-full absolute inset-0">
              {loading ? (
                <div className="flex flex-col items-center justify-center p-12">
                  <Loader2 className="h-8 w-8 animate-spin text-primary mb-2" />
                </div>
              ) : (
                <div className="p-6 pb-24 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                  {filteredFiles.map((item) => (
                    <div
                      key={item.id}
                      className="border rounded-xl p-4 flex flex-col items-center text-center gap-3 hover:bg-muted/50 hover:border-primary/50 cursor-pointer transition-all group relative aspect-square justify-center"
                      onClick={() => item.type === "folder" && navigateToFolder(item.path)}
                    >
                      {item.type === "folder" ? (
                        <Folder className="h-12 w-12 text-blue-500 fill-blue-500/20" />
                      ) : (
                        <div className="h-12 w-12 flex items-center justify-center">
                          {extensionIcon(item.extension, "h-10 w-10")}
                        </div>
                      )}

                      <div className="w-full">
                        <p className="font-medium text-sm truncate w-full" title={item.name}>{item.name}</p>
                        <p className="text-xs text-muted-foreground mt-1">
                          {item.type === "folder" ? "Pasta" : item.size || "-"}
                        </p>
                      </div>

                      {item.type === "file" && (
                        <Button
                          variant="secondary"
                          size="icon"
                          className="absolute top-2 right-2 h-7 w-7 opacity-0 group-hover:opacity-100 transition-opacity shadow-sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            const filePath = item.path ? normalizePath(item.path) : joinPath(currentPath, item.name);
                            setEditingFile({ path: filePath, name: item.name });
                          }}
                          title="Editar arquivo"
                        >
                          <Edit className="h-3.5 w-3.5" />
                        </Button>
                      )}
                    </div>
                  ))}
                  {filteredFiles.length === 0 && !loading && (
                    <div className="col-span-full flex flex-col items-center justify-center p-12 text-muted-foreground">
                      <Folder className="h-12 w-12 mb-4 text-muted-foreground/30" />
                      <p className="text-lg font-medium text-foreground">Diretório vazio</p>
                    </div>
                  )}
                </div>
              )}
            </ScrollArea>
          </div>
        )}
      </div>

      <div className="border-t px-4 py-2 bg-muted/20 text-xs text-muted-foreground flex justify-between items-center">
        <span>{statusText}</span>
        <span className="truncate max-w-[300px]" title={endpointHint ?? "webdav://clusterforge.local"}>
          {endpointHint ?? "webdav://clusterforge.local"}
        </span>
      </div>

      {editingFile && (
        <FileEditor
          filePath={editingFile.path}
          fileName={editingFile.name}
          isOpen={!!editingFile}
          onClose={() => setEditingFile(null)}
          onSave={() => {
            loadDirectory(currentPath);
          }}
        />
      )}
    </div>
  );
}





