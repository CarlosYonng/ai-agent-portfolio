import { useEffect, useMemo, useState } from "react";
import type { ChangeEvent, FormEvent, ReactNode } from "react";
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  listDocuments,
  uploadDocument,
  retryDocumentIngest,
  updateDocument,
  deleteDocument,
  downloadDocumentFile,
  getErrorMessage,
} from "./api";
import type { KnowledgeBase, KnowledgeDocument } from "./types";
import { StatusBadge } from "./PanelComponents";
import { useAuth } from "./AuthContext";

function DataTable({ headers, rows }: { headers: string[]; rows: Array<Array<ReactNode>> }) {
  const empty = useMemo(() => rows.length === 0, [rows]);
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr>
        </thead>
        <tbody>
          {empty ? (
            <tr><td colSpan={headers.length}>暂无数据</td></tr>
          ) : (
            rows.map((row, index) => (
              <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default function KnowledgeBasePanel() {
  const { user } = useAuth();
  const [spaces, setSpaces] = useState<KnowledgeBase[]>([]);
  const [selectedKb, setSelectedKb] = useState<number | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [editingKbId, setEditingKbId] = useState<number | null>(null);
  const [kbDetailTab, setKbDetailTab] = useState<"register" | "documents">("register");
  const [spaceName, setSpaceName] = useState("");
  const [spaceDescription, setSpaceDescription] = useState("");
  const [docTitle, setDocTitle] = useState("");
  const [docSourceType, setDocSourceType] = useState("MARKDOWN");
  const [docSourceUri, setDocSourceUri] = useState("");
  const [docFile, setDocFile] = useState<File | null>(null);
  const [folderFiles, setFolderFiles] = useState<File[]>([]);
  const [uploadMode, setUploadMode] = useState<"single" | "batch">("single");
  const [fileInputKey, setFileInputKey] = useState(0);
  const [folderInputKey, setFolderInputKey] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [batchUploading, setBatchUploading] = useState(false);
  const [retryingDocId, setRetryingDocId] = useState<number | null>(null);
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null);
  const [message, setMessage] = useState("");

  const currentSpace = spaces.find((space) => space.id === selectedKb);
  const isEditingDocument = selectedDocId !== null;
  const hasProcessingDocuments = useMemo(() => {
    return documents.some((document) => isDocumentProcessing(document));
  }, [documents]);
  const documentStatus = useMemo(() => {
    return documents.reduce((acc, document) => {
      const status = (document.status ?? "PENDING").toUpperCase();
      if (status === "INDEXED") acc.indexed += 1;
      else if (status === "FAILED") acc.failed += 1;
      else acc.pending += 1;
      return acc;
    }, { indexed: 0, pending: 0, failed: 0 });
  }, [documents]);

  function syncSpaceForm(space?: KnowledgeBase) {
    if (!space) {
      setEditingKbId(null);
      setSpaceName("");
      setSpaceDescription("");
      return;
    }
    setSpaceName(space.name);
    setSpaceDescription(space.description ?? "");
    setEditingKbId(space.id);
  }

  function syncDocumentForm(document?: KnowledgeDocument) {
    if (!document) {
      setSelectedDocId(null);
      setDocTitle("");
      setDocSourceType("MARKDOWN");
      setDocSourceUri("");
      setDocFile(null);
      setFolderFiles([]);
      setUploadMode("single");
      setFileInputKey((value) => value + 1);
      setFolderInputKey((value) => value + 1);
      return;
    }
    setSelectedDocId(document.id);
    setDocTitle(document.title);
    setDocSourceType(document.sourceType ?? "MARKDOWN");
    setDocSourceUri(document.sourceUri ?? "");
    setDocFile(null);
    setFolderFiles([]);
    setUploadMode("single");
    setFileInputKey((value) => value + 1);
    setFolderInputKey((value) => value + 1);
  }

  const refreshSpaces = async () => {
    const nextSpaces = await listKnowledgeBases();
    setSpaces(nextSpaces);
    return nextSpaces;
  };

  const refreshDocuments = async (kbId: number, syncForm = true) => {
    const nextDocuments = await listDocuments(kbId);
    setDocuments(nextDocuments);
    if (!syncForm) {
      return;
    }
    if (selectedDocId && nextDocuments.some((document) => document.id === selectedDocId)) {
      syncDocumentForm(nextDocuments.find((document) => document.id === selectedDocId));
    } else {
      syncDocumentForm();
    }
  };

  const refresh = async (kbId = selectedKb) => {
    const nextSpaces = await refreshSpaces();
    if (!kbId || !nextSpaces.some((space) => space.id === kbId)) {
      setSelectedKb(null);
      setDocuments([]);
      syncDocumentForm();
      return;
    }
    setSelectedKb(kbId);
    syncSpaceForm(nextSpaces.find((space) => space.id === kbId));
    await refreshDocuments(kbId);
  };

  useEffect(() => {
    refreshSpaces().catch((error) => setMessage(getErrorMessage(error, "知识库列表暂时加载失败，请稍后重试。")));
  }, []);

  useEffect(() => {
    if (!selectedKb || (!hasProcessingDocuments && !uploading && !batchUploading && retryingDocId === null)) {
      return;
    }
    const timer = window.setInterval(() => {
      listDocuments(selectedKb)
        .then(setDocuments)
        .catch((error) => setMessage(getErrorMessage(error, "文档状态刷新失败，请稍后重试。")));
    }, 2500);
    return () => window.clearInterval(timer);
  }, [selectedKb, hasProcessingDocuments, uploading, batchUploading, retryingDocId]);

  async function openKnowledgeBase(kbId: number) {
    setSelectedKb(kbId);
    setKbDetailTab("register");
    syncDocumentForm();
    await refreshDocuments(kbId);
  }

  async function submitSpace(event: FormEvent) {
    event.preventDefault();
    try {
      if (editingKbId) {
        await updateKnowledgeBase(editingKbId, {
          name: spaceName,
          description: spaceDescription
        });
        setMessage(`已更新知识库 #${editingKbId}`);
        await refresh(editingKbId);
        return;
      }
      await createKnowledgeBase({
        name: spaceName,
        description: spaceDescription,
        visibility: "PRIVATE"
      });
      setMessage(`已创建知识库"${spaceName}"，可在列表查看详情`);
      syncSpaceForm();
      await refreshSpaces();
    } catch (error) {
      setMessage(getErrorMessage(error, "知识库保存失败，请稍后重试。"));
    }
  }

  async function removeSpace(id = editingKbId) {
    if (!id || !window.confirm("删除知识库会同时删除文档元数据，确定继续？")) {
      return;
    }
    try {
      await deleteKnowledgeBase(id);
      setMessage(`已删除知识库 #${id}`);
      setSelectedKb(null);
      setDocuments([]);
      syncSpaceForm();
      syncDocumentForm();
      await refreshSpaces();
    } catch (error) {
      setMessage(getErrorMessage(error, "删除知识库失败，请稍后重试。"));
    }
  }

  async function submitDocument(event: FormEvent) {
    event.preventDefault();
    if (!selectedKb || !currentSpace) {
      setMessage("请先创建或选择知识库");
      return;
    }
    try {
      if (selectedDocId) {
        await updateDocument(selectedDocId, {
          title: docTitle,
          sourceType: docSourceType,
          sourceUri: docSourceUri
        });
        setMessage(`已保存文档 ${docTitle}`);
        setDocuments(await listDocuments(selectedKb));
        syncDocumentForm();
        return;
      }
      if (!docFile) {
        setMessage("请选择要上传的 Markdown 或 TXT 文档");
        return;
      }
      setUploading(true);
      const result = await uploadDocument(selectedKb, {
        file: docFile
      });
      setMessage(`已接收入库任务：${result.title}，可在文档列表查看处理进度`);
      setDocuments(await listDocuments(selectedKb));
      syncDocumentForm();
    } catch (error) {
      setMessage(getErrorMessage(error, "文档保存失败，请稍后重试。"));
    } finally {
      setUploading(false);
    }
  }

  function handleFolderChange(event: ChangeEvent<HTMLInputElement>) {
    const selectedFiles = Array.from(event.target.files ?? []);
    const supportedFiles = selectedFiles.filter((file) => isKnowledgeFile(file));
    setFolderFiles(supportedFiles);
    setDocFile(null);
    setDocTitle("");
    setFileInputKey((value) => value + 1);
    if (selectedFiles.length === 0) {
      setMessage("");
      return;
    }
    const skipped = selectedFiles.length - supportedFiles.length;
    setMessage(skipped > 0
      ? `已选择 ${supportedFiles.length} 个可入库文档，已忽略 ${skipped} 个非 md/txt 文件`
      : `已选择 ${supportedFiles.length} 个可入库文档`);
  }

  async function uploadFolderDocuments() {
    if (!selectedKb || !currentSpace) {
      setMessage("请先创建或选择知识库");
      return;
    }
    if (folderFiles.length === 0) {
      setMessage("请选择包含 Markdown 或 TXT 文档的文件夹");
      return;
    }
    setBatchUploading(true);
    let success = 0;
    let failed = 0;
    try {
      for (const file of folderFiles) {
        try {
          const result = await uploadDocument(selectedKb, {
            file
          });
          if ((result.status ?? "PENDING").toUpperCase() === "FAILED") {
            failed += 1;
          } else {
            success += 1;
          }
          setMessage(`批量入库中：已处理 ${success + failed}/${folderFiles.length}，当前文件：${file.name}`);
        } catch (error) {
          failed += 1;
          setMessage(getErrorMessage(error, `批量入库中：${file.name} 上传失败，继续处理后续文件。`));
        }
      }
      setDocuments(await listDocuments(selectedKb));
      setFolderFiles([]);
      setFolderInputKey((value) => value + 1);
      setMessage(`文件夹批量任务已提交：成功 ${success} 个，失败 ${failed} 个；可在文档列表查看后续进度`);
    } finally {
      setBatchUploading(false);
    }
  }

  async function removeDocument(id: number) {
    if (!window.confirm("删除文档元数据后，需要另行清理向量和图谱索引，确定继续？")) {
      return;
    }
    try {
      await deleteDocument(id);
      setMessage(`已删除文档 #${id}`);
      if (selectedKb) {
        await refresh(selectedKb);
      }
    } catch (error) {
      setMessage(getErrorMessage(error, "删除文档失败，请稍后重试。"));
    }
  }

  async function downloadDocument(doc: KnowledgeDocument) {
    try {
      const { blob, filename } = await downloadDocumentFile(doc.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setMessage(`已开始下载 ${doc.title}`);
    } catch (error) {
      setMessage(getErrorMessage(error, "文档下载失败，请稍后重试。"));
    }
  }

  async function retryDocument(doc: KnowledgeDocument) {
    setRetryingDocId(doc.id);
    try {
      const result = await retryDocumentIngest(doc.id);
      setMessage(`已重新提交入库任务：${result.title}`);
      if (selectedKb) {
        await refreshDocuments(selectedKb, false);
      }
    } catch (error) {
      setMessage(getErrorMessage(error, "重试入库失败，请稍后重试。"));
    } finally {
      setRetryingDocId(null);
    }
  }

  // ========== 外层：知识库列表 + 新建/编辑表单 ==========

  if (!selectedKb || !currentSpace) {
    return (
      <section className="content-grid workspace-grid">
        {user?.role !== "USER" ? (
        <form className="panel span-4" onSubmit={submitSpace}>
          <div className="panel-title">
            <h2>{editingKbId ? "编辑知识库" : "新建知识库"}</h2>
            <span>{message || "先维护知识库，再进入详情登记文档"}</span>
          </div>
          <label>
            <span>名称 <span className="text-red-500 font-bold">*</span></span>
            <input value={spaceName} onChange={(event) => setSpaceName(event.target.value)} placeholder="例如：支付知识库" required />
          </label>
          <label>
            说明
            <textarea value={spaceDescription} onChange={(event) => setSpaceDescription(event.target.value)} placeholder="描述该知识库的业务范围" />
          </label>
          <div className="button-row">
            <button className="primary" type="submit">{editingKbId ? "保存修改" : "创建知识库"}</button>
            <button className="ghost" type="button" onClick={() => syncSpaceForm()}>重置</button>
          </div>
        </form>
        ) : null}

        <div className="panel span-8">
          <div className="panel-title">
            <h2>知识库列表</h2>
            <span>{spaces.length} 个空间 · 点击进入后维护文档</span>
          </div>
          <DataTable
            headers={["ID", "名称", "说明", "操作"]}
            rows={spaces.map((space) => [
              space.id,
              <strong key={`name-${space.id}`}>{space.name}</strong>,
              <span className="kb-description" title={space.description || "暂无说明"} key={`desc-${space.id}`}>{space.description || "-"}</span>,
              <div className="table-actions" key={`actions-${space.id}`}>
                <button className="primary compact" type="button" onClick={() => openKnowledgeBase(space.id)}>进入</button>
                <button className="ghost compact" type="button" onClick={() => syncSpaceForm(space)}>编辑</button>
                <button className="danger compact" type="button" onClick={() => removeSpace(space.id)}>删除</button>
              </div>
            ])}
          />
        </div>
      </section>
    );
  }

  // ========== 内层：知识库详情 ==========

  return (
    <section className="content-grid workspace-grid">
      <div className="span-12 compact-toolbar">
        <button className="ghost compact" type="button" onClick={() => { setSelectedKb(null); setDocuments([]); syncDocumentForm(); }}>
          返回知识库列表
        </button>
        <span className="mx-1.5 text-slate-300 select-none">/</span>
        <span className="text-sm font-semibold text-slate-800">{currentSpace.name}</span>
      </div>

      <div className="panel span-12">
        <div className="document-status-strip">
          <div>
            <span>已入库</span>
            <strong>{documentStatus.indexed}</strong>
          </div>
          <div>
            <span>处理中</span>
            <strong>{documentStatus.pending}</strong>
          </div>
          <div>
            <span>失败</span>
            <strong>{documentStatus.failed}</strong>
          </div>
        </div>

        <div className="tabs">
          <button className={kbDetailTab === "register" ? "tab active" : "tab"} type="button" onClick={() => setKbDetailTab("register")}>
            文档登记
          </button>
          <button className={kbDetailTab === "documents" ? "tab active" : "tab"} type="button" onClick={() => setKbDetailTab("documents")}>
            文档列表
          </button>
        </div>

        {kbDetailTab === "register" ? (
          <form className="tab-body document-form" onSubmit={submitDocument}>
            <div className="panel-title">
              <h2>{isEditingDocument ? "编辑文档" : "上传文档"}</h2>
              <span>{message || `${currentSpace.name} · 上传后自动切片并触发索引`}</span>
            </div>
            {isEditingDocument ? (
              <>
                <label>
                  <span>文档标题 <span className="text-red-500 font-bold">*</span></span>
                  <input value={docTitle} onChange={(event) => setDocTitle(event.target.value)} placeholder="例如：payment_callback.md" required />
                </label>
                <label>
                  来源类型
                  <select value={docSourceType} onChange={(event) => setDocSourceType(event.target.value)}>
                    <option value="MARKDOWN">MARKDOWN</option>
                    <option value="TEXT">TEXT</option>
                    <option value="PDF">PDF</option>
                    <option value="URL">URL</option>
                    <option value="DOCX">DOCX</option>
                  </select>
                </label>
                <label>
                  来源地址
                  <input value={docSourceUri} onChange={(event) => setDocSourceUri(event.target.value)} placeholder="例如：data/uploads/kb-docs/..." />
                </label>
              </>
            ) : (
              <>
                <div className="upload-mode-switch" role="tablist" aria-label="上传方式">
                  <button
                    className={uploadMode === "single" ? "active" : ""}
                    type="button"
                    onClick={() => {
                      setUploadMode("single");
                      setFolderFiles([]);
                      setFolderInputKey((value) => value + 1);
                    }}
                  >
                    单个文件
                  </button>
                  <button
                    className={uploadMode === "batch" ? "active" : ""}
                    type="button"
                    onClick={() => {
                      setUploadMode("batch");
                      setDocFile(null);
                      setFileInputKey((value) => value + 1);
                    }}
                  >
                    文件夹批量
                  </button>
                </div>
                {uploadMode === "single" ? (
                  <div className="upload-panel">
                    <label>
                      <span>选择 Markdown 或 TXT 文件 <span className="text-red-500 font-bold">*</span></span>
                      <input
                        key={fileInputKey}
                        type="file"
                        accept=".md,.txt,text/markdown,text/plain"
                        onChange={(event) => {
                          const file = event.target.files?.[0] ?? null;
                          setDocFile(file);
                          setFolderFiles([]);
                          setFolderInputKey((value) => value + 1);
                        }}
                        required
                      />
                    </label>
                    <span className="form-hint">文档标题将自动使用上传文件名，入库后可在文档列表中编辑。</span>
                    {docFile ? (
                      <div className="upload-selection" title={docFile.name}>
                        <strong>{docFile.name}</strong>
                        <span>{formatFileSize(docFile.size)}</span>
                      </div>
                    ) : null}
                  </div>
                ) : (
                  <div className="upload-panel">
                    <label>
                      <span>选择包含文档的文件夹 <span className="text-red-500 font-bold">*</span></span>
                      <input
                        key={folderInputKey}
                        type="file"
                        accept=".md,.txt,text/markdown,text/plain"
                        multiple
                        onChange={handleFolderChange}
                        {...{ webkitdirectory: "", directory: "" }}
                      />
                    </label>
                    <span className="form-hint">
                      只会上传文件夹中的 Markdown 和 TXT 文档；每个文档标题自动使用对应文件名。
                    </span>
                    {folderFiles.length > 0 ? (
                      <div className="folder-upload-summary">
                        <strong>{folderFiles.length}</strong>
                        <span>个文档待批量入库</span>
                      </div>
                    ) : null}
                    <button className="ghost" type="button" disabled={batchUploading || uploading || folderFiles.length === 0} onClick={uploadFolderDocuments}>
                      {batchUploading ? "批量入库中..." : "上传所选文件夹"}
                    </button>
                  </div>
                )}
              </>
            )}
            <div className="button-row">
              {(isEditingDocument || uploadMode === "single") ? (
                <button className="primary" type="submit" disabled={uploading || batchUploading}>{isEditingDocument ? "保存修改" : uploading ? "入库中..." : "上传单个文件"}</button>
              ) : null}
              <button className="ghost" type="button" onClick={() => syncDocumentForm()}>{isEditingDocument ? "取消编辑" : "清空表单"}</button>
            </div>
          </form>
        ) : (
          <div className="tab-body">
            <div className="panel-title">
              <h2>文档列表</h2>
              <span>{documents.length} 条记录 · 入库中自动刷新阶段、进度和失败原因</span>
            </div>
            <DataTable
              headers={["标题", "来源", "状态", "进度", "版本", "操作"]}
              rows={documents.map((doc) => [
                doc.title,
                doc.sourceType ?? "-",
                <StatusBadge key={`status-${doc.id}`} value={doc.status ?? "PENDING"} />,
                <DocumentProgress key={`progress-${doc.id}`} document={doc} />,
                doc.version ?? "-",
                <div className="table-actions" key={`actions-${doc.id}`}>
                  <button className="ghost compact" type="button" onClick={() => downloadDocument(doc)}>下载</button>
                  {(doc.status ?? "").toUpperCase() === "FAILED" ? (
                    <button className="ghost compact" type="button" disabled={retryingDocId === doc.id} onClick={() => retryDocument(doc)}>
                      {retryingDocId === doc.id ? "重试中..." : "重试入库"}
                    </button>
                  ) : null}
                  <button className="ghost compact" type="button" onClick={() => { syncDocumentForm(doc); setKbDetailTab("register"); }}>编辑</button>
                  <button className="danger compact" type="button" onClick={() => removeDocument(doc.id)}>删除</button>
                </div>
              ])}
            />
          </div>
        )}
      </div>
    </section>
  );
}

function isKnowledgeFile(file: File) {
  const filename = file.name.toLowerCase();
  return filename.endsWith(".md") || filename.endsWith(".txt");
}

function DocumentProgress({ document }: { document: KnowledgeDocument }) {
  const status = (document.status ?? "PENDING").toUpperCase();
  const progress = normalizeProgress(document.progressPercent, status);
  const chunkText = document.chunkTotal != null
    ? `${document.chunkDone ?? 0}/${document.chunkTotal} 片`
    : "";
  const error = status === "FAILED" && document.errorMessage ? trimError(document.errorMessage) : "";
  return (
    <div className="document-progress">
      <div className="progress-meta">
        <span>{stageText(document.ingestStage, status)}</span>
        <strong>{progress}%</strong>
      </div>
      <div className="progress-bar" aria-label={`入库进度 ${progress}%`}>
        <span style={{ width: `${progress}%` }} />
      </div>
      <div className="progress-detail">
        {chunkText ? <small>{chunkText}</small> : null}
        {error ? <small className="progress-error" title={document.errorMessage}>{error}</small> : null}
      </div>
    </div>
  );
}

function isDocumentProcessing(document: KnowledgeDocument) {
  const status = (document.status ?? "PENDING").toUpperCase();
  return status === "PENDING";
}

function normalizeProgress(value: number | undefined, status: string) {
  if (status === "INDEXED") {
    return 100;
  }
  if (status === "FAILED" && value == null) {
    return 0;
  }
  return Math.max(0, Math.min(100, Math.round(value ?? 0)));
}

function stageText(stage: string | undefined, status: string) {
  if (status === "INDEXED") return "已完成";
  if (status === "FAILED") return "入库失败";
  const normalized = (stage ?? "PENDING").toUpperCase();
  const labels: Record<string, string> = {
    REGISTERED: "已登记",
    UPLOADED: "已上传",
    RETRYING: "重试中",
    READING: "读取文件",
    CHUNKING: "文档分割",
    EMBEDDING: "向量化",
    WRITING_VECTOR: "写入向量库",
    INDEXED: "已完成",
    FAILED: "入库失败",
    PENDING: "等待处理",
  };
  return labels[normalized] ?? normalized;
}

function trimError(message: string) {
  return message.length > 80 ? `${message.slice(0, 80)}...` : message;
}

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
