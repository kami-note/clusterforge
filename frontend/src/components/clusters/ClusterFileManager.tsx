import { ReactNode, useMemo, useState } from "react";
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
  File
} from "lucide-react";

interface FileNode {
  id: string;
  name: string;
  type: "folder" | "file";
  size?: string;
  modifiedAt: string;
  children?: FileNode[];
  extension?: string;
}

const mockStructure: FileNode = {
  id: "root",
  name: "Este Computador",
  type: "folder",
  modifiedAt: "2025-11-14 09:12",
  children: [
    {
      id: "cluster-drive",
      name: "Cluster (C:)",
      type: "folder",
      modifiedAt: "2025-11-14 08:04",
      children: [
        {
          id: "app",
          name: "app",
          type: "folder",
          modifiedAt: "2025-11-14 07:55",
          children: [
            { id: "controllers", name: "controllers", type: "folder", modifiedAt: "2025-11-14 07:00", children: [] },
            { id: "services", name: "services", type: "folder", modifiedAt: "2025-11-13 18:33", children: [] },
            { id: "Dockerfile", name: "Dockerfile", type: "file", size: "2 KB", modifiedAt: "2025-11-12 22:10", extension: "docker" }
          ]
        },
        {
          id: "public",
          name: "public",
          type: "folder",
          modifiedAt: "2025-11-12 11:22",
          children: [
            { id: "assets", name: "assets", type: "folder", modifiedAt: "2025-11-12 11:20", children: [] },
            { id: "index.html", name: "index.html", type: "file", size: "6 KB", modifiedAt: "2025-11-10 21:18", extension: "html" },
            { id: "favicon.ico", name: "favicon.ico", type: "file", size: "1 KB", modifiedAt: "2025-11-10 21:19", extension: "ico" }
          ]
        },
        {
          id: "storage",
          name: "storage",
          type: "folder",
          modifiedAt: "2025-11-11 15:44",
          children: [
            { id: "logs", name: "logs", type: "folder", modifiedAt: "2025-11-11 15:40", children: [] },
            { id: "backups", name: "backups", type: "folder", modifiedAt: "2025-11-11 15:41", children: [] }
          ]
        }
      ]
    },
    {
      id: "webdav-drive",
      name: "WebDAV (W:)",
      type: "folder",
      modifiedAt: "2025-11-14 09:00",
      children: [
        {
          id: "shared",
          name: "shared",
          type: "folder",
          modifiedAt: "2025-11-13 13:00",
          children: [
            { id: "readme.txt", name: "readme.txt", type: "file", size: "1 KB", modifiedAt: "2025-11-13 12:45", extension: "txt" }
          ]
        }
      ]
    }
  ]
};

interface ClusterFileManagerProps {
  clusterName: string;
  endpointHint?: string;
}

const flattenNodes = (node: FileNode): Record<string, FileNode> => {
  const map: Record<string, FileNode> = { [node.id]: node };
  node.children?.forEach((child) => {
    Object.assign(map, flattenNodes(child));
  });
  return map;
};

const buildBreadcrumb = (nodeMap: Record<string, FileNode>, nodeId: string): FileNode[] => {
  const breadcrumb: FileNode[] = [];
  let current: FileNode | undefined = nodeMap[nodeId];

  while (current) {
    breadcrumb.unshift(current);
    const parent = Object.values(nodeMap).find((candidate) => candidate.children?.some((child) => child.id === current!.id));
    current = parent;
  }

  return breadcrumb;
};

const getFolderChildren = (nodeMap: Record<string, FileNode>, nodeId: string): FileNode[] => {
  const node = nodeMap[nodeId];
  if (!node || node.type !== "folder") return [];
  return node.children ?? [];
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

export function ClusterFileManager({ clusterName, endpointHint }: ClusterFileManagerProps) {
  const nodeMap = useMemo(() => flattenNodes(mockStructure), []);
  const [selectedFolderId, setSelectedFolderId] = useState<string>("cluster-drive");
  const [viewMode, setViewMode] = useState<"list" | "grid">("list");
  const [searchTerm, setSearchTerm] = useState("");

  const breadcrumb = buildBreadcrumb(nodeMap, selectedFolderId);
  const children = getFolderChildren(nodeMap, selectedFolderId).filter((child) =>
    child.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const statusText = `${children.length} item${children.length === 1 ? "" : "s"}`;

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
          <Button variant="outline" size="sm">
            <RefreshCw className="h-4 w-4 mr-2" />
            Sincronizar
          </Button>
          <Button variant="outline" size="sm">
            <Cloud className="h-4 w-4 mr-2" />
            Conectar
          </Button>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon">
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon">
            <ArrowRight className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon">
            <ChevronsUpDown className="h-4 w-4" />
          </Button>
        </div>
        <div className="flex-1 flex items-center gap-2 bg-muted rounded-lg px-2 py-1 border">
          <HardDrive className="h-4 w-4 text-muted-foreground" />
          <div className="flex items-center flex-wrap gap-1 text-sm">
            {breadcrumb.map((node, index) => (
              <div key={node.id} className="flex items-center gap-1">
                <button
                  className="hover:underline"
                  onClick={() => node.type === "folder" && setSelectedFolderId(node.id)}
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
          <Button variant="ghost" size="sm">
            <FolderPlus className="h-4 w-4 mr-2" />
            Nova pasta
          </Button>
          <Button variant="ghost" size="sm">
            <Upload className="h-4 w-4 mr-2" />
            Upload
          </Button>
          <Separator orientation="vertical" className="h-6" />
          <Button variant="ghost" size="sm">
            <FolderKanban className="h-4 w-4 mr-2" />
            Mapear WebDAV
          </Button>
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

      <div className="grid grid-cols-12 gap-4">
        <CardLikePanel title="Pastas" className="col-span-3 min-h-[320px]">
          <ScrollArea className="h-[260px] pr-2">
            <TreeView
              nodes={mockStructure.children ?? []}
              selectedId={selectedFolderId}
              onSelect={(id) => setSelectedFolderId(id)}
            />
          </ScrollArea>
        </CardLikePanel>

        <CardLikePanel
          title={`Conteúdo de ${nodeMap[selectedFolderId]?.name ?? ""}`}
          className="col-span-9 min-h-[320px]"
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
                {children.length === 0 && (
                  <div className="text-sm text-muted-foreground px-4 py-6">
                    Nenhum item encontrado. Utilize o botão Upload ou crie uma nova pasta.
                  </div>
                )}
                {children.map((item) => (
                  <button
                    key={item.id}
                    className="grid grid-cols-12 items-center px-4 py-2 text-sm w-full text-left hover:bg-muted/70"
                    onClick={() => item.type === "folder" && setSelectedFolderId(item.id)}
                  >
                    <span className="col-span-5 flex items-center gap-2">
                      {item.type === "folder" ? (
                        <Folder className="h-4 w-4 text-primary" />
                      ) : (
                        extensionIcon(item.extension)
                      )}
                      {item.name}
                    </span>
                    <span className="col-span-3">{item.type === "folder" ? "Pasta" : item.extension?.toUpperCase()}</span>
                    <span className="col-span-2">{item.modifiedAt}</span>
                    <span className="col-span-2 text-right">{item.type === "folder" ? "-" : item.size ?? "-"}</span>
                  </button>
                ))}
              </ScrollArea>
            </div>
          ) : (
            <ScrollArea className="h-[280px]">
              <div className="grid grid-cols-3 gap-4 pr-2">
                {children.map((item) => (
                  <button
                    key={item.id}
                    className="border rounded-lg p-3 text-left hover:border-primary"
                    onClick={() => item.type === "folder" && setSelectedFolderId(item.id)}
                  >
                    <div className="flex items-center gap-3">
                      {item.type === "folder" ? (
                        <Folder className="h-10 w-10 text-primary" />
                      ) : (
                        extensionIcon(item.extension)
                      )}
                      <div>
                        <p className="font-medium">{item.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {item.type === "folder" ? "Pasta" : `${item.extension?.toUpperCase()} • ${item.size}`}
                        </p>
                      </div>
                    </div>
                  </button>
                ))}
                {children.length === 0 && (
                  <div className="text-sm text-muted-foreground px-4 py-6 col-span-3">
                    Nenhum item encontrado. Utilize o botão Upload ou crie uma nova pasta.
                  </div>
                )}
              </div>
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

interface TreeViewProps {
  nodes: FileNode[];
  selectedId: string;
  onSelect: (id: string) => void;
}

function TreeView({ nodes, selectedId, onSelect }: TreeViewProps) {
  return (
    <div className="space-y-1">
      {nodes.map((node) => (
        <div key={node.id}>
          <button
            className={`flex items-center gap-2 text-sm px-2 py-1 rounded w-full text-left ${
              selectedId === node.id ? "bg-primary/10 text-primary" : "hover:bg-muted"
            }`}
            onClick={() => node.type === "folder" && onSelect(node.id)}
          >
            {node.type === "folder" ? <Folder className="h-4 w-4" /> : <File className="h-4 w-4" />}
            {node.name}
          </button>
          {node.children && node.children.length > 0 && (
            <div className="pl-4 border-l ml-2 mt-1">
              <TreeView nodes={node.children} selectedId={selectedId} onSelect={onSelect} />
            </div>
          )}
        </div>
      ))}
    </div>
  );
}


