"use client";

import Image from "next/image";
import { Link } from "@/i18n/navigation";
import { useParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";

import GroupSectionNav from "@/components/groups/GroupSectionNav";
import { apiImageUrl, apiRequest, apiRequestWithCsrf } from "@/lib/api";
import { getCurrencies } from "@/lib/currencies";

type Group = {
  id: string;
  name: string;
  description: string;
  image: string;
  currency: string;
};

type GroupMember = {
  id: string;
  name: string;
};

type GroupActivityLog = {
  id: string;
  type: string;
  date: string;
  madeByUsername: string;
};

type GroupActivityPage = {
  content: GroupActivityLog[];
  number: number;
  totalPages: number;
};

type LoadState = "loading" | "ready" | "error";

const ACTIVITY_PAGE_SIZE = 10;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseGroup(value: unknown): Group {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.name !== "string" ||
    (value.description != null && typeof value.description !== "string") ||
    (value.img != null && typeof value.img !== "string") ||
    typeof value.currency !== "string" ||
    !/^[A-Z]{3}$/.test(value.currency)
  ) {
    throw new Error("The API returned invalid group details.");
  }

  return {
    id: value.id,
    name: value.name,
    description: typeof value.description === "string" ? value.description : "",
    image: typeof value.img === "string" ? value.img : "",
    currency: value.currency,
  };
}

function parseMembers(value: unknown): GroupMember[] {
  if (!Array.isArray(value)) {
    throw new Error("The API returned an invalid members list.");
  }

  return value.map((item) => {
    if (
      !isRecord(item) ||
      typeof item.id !== "string" ||
      typeof item.name !== "string"
    ) {
      throw new Error("The API returned an invalid group member.");
    }
    return { id: item.id, name: item.name };
  });
}

function parseActivityPage(value: unknown): GroupActivityPage {
  if (
    !isRecord(value) ||
    !Array.isArray(value.content) ||
    typeof value.number !== "number" ||
    !Number.isInteger(value.number) ||
    value.number < 0 ||
    typeof value.totalPages !== "number" ||
    !Number.isInteger(value.totalPages) ||
    value.totalPages < 0
  ) {
    throw new Error("The API returned an invalid activity log page.");
  }

  const content = value.content.map((item) => {
    if (
      !isRecord(item) ||
      typeof item.id !== "string" ||
      typeof item.type !== "string" ||
      typeof item.date !== "string" ||
      Number.isNaN(Date.parse(item.date)) ||
      typeof item.madeByUsername !== "string"
    ) {
      throw new Error("The API returned an invalid activity log entry.");
    }

    return {
      id: item.id,
      type: item.type,
      date: item.date,
      madeByUsername: item.madeByUsername,
    };
  });

  return {
    content,
    number: value.number,
    totalPages: value.totalPages,
  };
}

function describeActivity(
  log: GroupActivityLog,
  t: ReturnType<typeof useTranslations<"Settings">>,
): string {
  switch (log.type) {
    case "EXPENSE_ADDED":
      return t("expenseAdded", { name: log.madeByUsername });
    case "GROUP_UPDATED":
      return t("groupUpdated", { name: log.madeByUsername });
    default:
      return t("activityRecorded", { name: log.madeByUsername });
  }
}

function formatActivityDate(date: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(date));
}

export default function GroupSettingsPage() {
  const { groupId } = useParams<{ groupId: string }>();
  const locale = useLocale();
  const t = useTranslations("Settings");
  const commonT = useTranslations("Common");
  const errorsT = useTranslations("Errors");
  const currencies = getCurrencies(locale);
  const imageFileRef = useRef<HTMLInputElement>(null);
  const [group, setGroup] = useState<Group | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [activityLogs, setActivityLogs] = useState<GroupActivityLog[]>([]);
  const [activityPage, setActivityPage] = useState(0);
  const [activityTotalPages, setActivityTotalPages] = useState(0);
  const [activityRetryKey, setActivityRetryKey] = useState(0);
  const [activityLoadState, setActivityLoadState] = useState<LoadState>("loading");
  const [activityError, setActivityError] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [image, setImage] = useState("");
  const [selectedImage, setSelectedImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [currency, setCurrency] = useState("");
  const [memberName, setMemberName] = useState("");
  const [saving, setSaving] = useState(false);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [imageUploadError, setImageUploadError] = useState("");
  const [imageUploadMessage, setImageUploadMessage] = useState("");
  const [addingMember, setAddingMember] = useState(false);
  const [removingMemberId, setRemovingMemberId] = useState<string | null>(null);
  const [settingsMessage, setSettingsMessage] = useState("");
  const [settingsError, setSettingsError] = useState("");
  const [memberError, setMemberError] = useState("");
  const [memberMessage, setMemberMessage] = useState("");

  useEffect(
    () => () => {
      if (imagePreview) URL.revokeObjectURL(imagePreview);
    },
    [imagePreview],
  );

  const retry = useCallback(() => {
    setLoadError("");
    setLoadState("loading");
    setGroup(null);
    setMembers([]);
    setRefreshKey((current) => current + 1);
    setActivityLogs([]);
    setActivityPage(0);
    setActivityTotalPages(0);
    setActivityRetryKey((current) => current + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    async function loadSettings() {
      try {
        const encodedGroupId = encodeURIComponent(groupId);
        const [groupResponse, membersResponse] = await Promise.all([
          apiRequest(`/api/groups/${encodedGroupId}`, {
            cache: "no-store",
            signal: controller.signal,
          }),
          apiRequest(`/api/groups/${encodedGroupId}/members`, {
            cache: "no-store",
            signal: controller.signal,
          }),
        ]);

        if (!groupResponse.ok) {
          throw new Error(groupResponse.status === 404
            ? errorsT("groupNotFound")
            : errorsT("loadGroupDetails", { status: groupResponse.status }));
        }
        if (!membersResponse.ok) {
          throw new Error(errorsT("loadGroupMembers", { status: membersResponse.status }));
        }

        const [loadedGroup, loadedMembers] = await Promise.all([
          groupResponse.json().then(parseGroup),
          membersResponse.json().then(parseMembers),
        ]);

        if (!controller.signal.aborted) {
          setGroup(loadedGroup);
          setMembers(loadedMembers);
          setName(loadedGroup.name);
          setDescription(loadedGroup.description);
          setImage(loadedGroup.image);
          setCurrency(loadedGroup.currency);
          setLoadState("ready");
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        setLoadError(
          error instanceof Error &&
          !error.message.startsWith("The API returned") &&
          error.name !== "TypeError" &&
          error.name !== "SyntaxError"
            ? error.message
            : commonT("unexpectedError"),
        );
        setLoadState("error");
      }
    }

    void loadSettings();
    return () => controller.abort();
  }, [commonT, errorsT, groupId, refreshKey]);

  useEffect(() => {
    const controller = new AbortController();

    async function loadActivity() {
      setActivityLoadState("loading");
      setActivityError("");

      try {
        const response = await apiRequest(
          `/api/groups/${encodeURIComponent(groupId)}/logs?page=${activityPage}&size=${ACTIVITY_PAGE_SIZE}`,
          { cache: "no-store", signal: controller.signal },
        );
        if (!response.ok) {
          throw new Error(errorsT("loadActivity", { status: response.status }));
        }

        const page = parseActivityPage(await response.json());
        if (!controller.signal.aborted) {
          setActivityLogs((current) =>
            activityPage === 0 ? page.content : [...current, ...page.content],
          );
          setActivityTotalPages(page.totalPages);
          setActivityLoadState("ready");
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        setActivityError(
          error instanceof Error &&
          !error.message.startsWith("The API returned") &&
          error.name !== "TypeError" &&
          error.name !== "SyntaxError"
            ? error.message
            : commonT("unexpectedError"),
        );
        setActivityLoadState("error");
      }
    }

    void loadActivity();
    return () => controller.abort();
  }, [activityPage, activityRetryKey, commonT, errorsT, groupId]);

  async function saveSettings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!group) return;

    setSaving(true);
    setSettingsMessage("");
    setSettingsError("");
    try {
      const response = await apiRequestWithCsrf(
        `/api/groups/${encodeURIComponent(group.id)}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            name: name.trim(),
            description: description.trim() || null,
            img: image.trim() || null,
            currency,
          }),
        },
      );
      if (!response.ok) {
        throw new Error(errorsT("saveSettings", { status: response.status }));
      }

      const updatedGroup = parseGroup(await response.json());
      setGroup(updatedGroup);
      setName(updatedGroup.name);
      setDescription(updatedGroup.description);
      setImage(updatedGroup.image);
      setCurrency(updatedGroup.currency);
      setActivityLogs([]);
      setActivityTotalPages(0);
      setActivityPage(0);
      setActivityRetryKey((current) => current + 1);
      setSettingsMessage(t("settingsSaved"));
    } catch (error) {
      setSettingsError(
        error instanceof Error &&
        !error.message.startsWith("The API returned") &&
        error.name !== "TypeError" &&
        error.name !== "SyntaxError"
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setSaving(false);
    }
  }

  async function uploadGroupImage() {
    if (!group || !selectedImage) return;

    setUploadingImage(true);
    setImageUploadError("");
    setImageUploadMessage("");
    try {
      const formData = new FormData();
      formData.set("image", selectedImage);
      const response = await apiRequestWithCsrf(
        `/api/groups/${encodeURIComponent(group.id)}/image`,
        { method: "PUT", body: formData },
      );
      if (!response.ok) {
        throw new Error(errorsT("uploadImage", { status: response.status }));
      }

      const updatedGroup = parseGroup(await response.json());
      setGroup(updatedGroup);
      setImage(updatedGroup.image);
      setSelectedImage(null);
      setImagePreview(null);
      if (imageFileRef.current) imageFileRef.current.value = "";
      setImageUploadMessage(t("imageUploaded"));
    } catch (error) {
      setImageUploadError(
        error instanceof Error &&
        !error.message.startsWith("The API returned") &&
        error.name !== "TypeError" &&
        error.name !== "SyntaxError"
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setUploadingImage(false);
    }
  }

  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!group || !memberName.trim()) return;

    setAddingMember(true);
    setMemberError("");
    setMemberMessage("");
    try {
      const response = await apiRequestWithCsrf(
        `/api/groups/${encodeURIComponent(group.id)}/members`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: memberName.trim() }),
        },
      );
      if (!response.ok) {
        throw new Error(errorsT("addMember", { status: response.status }));
      }

      const newMember = parseMembers([await response.json()])[0];
      setMembers((current) => [...current, newMember]);
      setMemberName("");
      setMemberMessage(t("memberAdded", { name: newMember.name }));
    } catch (error) {
      setMemberError(
        error instanceof Error &&
        !error.message.startsWith("The API returned") &&
        error.name !== "TypeError" &&
        error.name !== "SyntaxError"
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setAddingMember(false);
    }
  }

  async function removeMember(member: GroupMember) {
    if (
      !window.confirm(t("confirmRemove", { name: member.name }))
    ) {
      return;
    }

    setRemovingMemberId(member.id);
    setMemberError("");
    setMemberMessage("");
    try {
      const response = await apiRequestWithCsrf(
        `/api/members/${encodeURIComponent(member.id)}`,
        { method: "DELETE" },
      );
      if (!response.ok) {
        throw new Error(errorsT("removeMember", { status: response.status }));
      }

      setMembers((current) => current.filter((item) => item.id !== member.id));
      setMemberMessage(t("memberRemoved", { name: member.name }));
    } catch (error) {
      setMemberError(
        error instanceof Error &&
        !error.message.startsWith("The API returned") &&
        error.name !== "TypeError" &&
        error.name !== "SyntaxError"
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setRemovingMemberId(null);
    }
  }

  return (
    <main className="group-detail">
      <header className="group-detail__header">
        <Link
          aria-label={commonT("backToGroups")}
          className="group-detail__back"
          href="/"
        >
          <Image alt="" height={32} src="/group-detail/back-mobile.svg" width={32} />
        </Link>
        <h1>{group?.name}</h1>
      </header>

      <GroupSectionNav activeSection="settings" groupId={groupId} />

      <section aria-label={t("section")} className="group-detail__content group-settings">
        {loadState === "loading" && (
          <p className="group-detail__status" role="status">{t("loading")}</p>
        )}
        {loadState === "error" && (
          <div className="group-detail__message" role="alert">
            <p>{loadError}</p>
            <button onClick={retry} type="button">{commonT("tryAgain")}</button>
          </div>
        )}
        {loadState === "ready" && (
          <>
            <form className="group-settings__card group-settings__information" onSubmit={saveSettings}>
              <h2>{t("information")}</h2>
              <div className="group-settings__fields">
                <label>
                  {t("groupName")}
                  <input
                    maxLength={100}
                    onChange={(event) => setName(event.target.value)}
                    required
                    value={name}
                  />
                </label>
                <label>
                  {t("currency")}
                  <select
                    onChange={(event) => setCurrency(event.target.value)}
                    required
                    value={currency}
                  >
                    {currency && !currencies.some(({ code }) => code === currency) && (
                      <option value={currency}>{currency}</option>
                    )}
                    {currencies.map(({ code, label }) => (
                      <option key={code} value={code}>{label}</option>
                    ))}
                  </select>
                </label>
                <label className="group-settings__description">
                  {t("description")}
                  <textarea
                    maxLength={1000}
                    onChange={(event) => setDescription(event.target.value)}
                    rows={4}
                    value={description}
                  />
                </label>
              </div>
              <div className="group-settings__image">
                <span className="group-settings__image-title">{t("groupImage")}</span>
                <button
                  aria-label={t("chooseImage")}
                  className="group-settings__image-preview"
                  disabled={uploadingImage}
                  onClick={() => imageFileRef.current?.click()}
                  type="button"
                >
                  <Image
                    alt=""
                    className="group-settings__image-picture"
                    crossOrigin={!imagePreview && image.startsWith("/api/") ? "use-credentials" : undefined}
                    height={170}
                    src={imagePreview || apiImageUrl(image) || "/group-detail/group-image-placeholder.svg"}
                    unoptimized
                    width={170}
                  />
                  <Image
                    alt=""
                    className="group-settings__image-add"
                    height={40}
                    src="/group-detail/group-image-add.svg"
                    unoptimized
                    width={40}
                  />
                </button>
                <input
                  accept="image/png,image/jpeg"
                  aria-label={t("chooseImageFile")}
                  className="group-settings__image-input"
                  disabled={uploadingImage}
                  onChange={(event) => {
                    setImageUploadError("");
                    setImageUploadMessage("");
                    const file = event.target.files?.[0] ?? null;
                    if (file && file.type !== "image/png" && file.type !== "image/jpeg") {
                      setSelectedImage(null);
                      setImagePreview(null);
                      event.target.value = "";
                      setImageUploadError(t("invalidImageType"));
                      return;
                    }
                    if (file && file.size > 5 * 1024 * 1024) {
                      setSelectedImage(null);
                      setImagePreview(null);
                      event.target.value = "";
                      setImageUploadError(t("imageTooLarge"));
                      return;
                    }
                    setSelectedImage(file);
                    setImagePreview(file ? URL.createObjectURL(file) : null);
                  }}
                  ref={imageFileRef}
                  type="file"
                />
                <p className="group-settings__image-hint">{t("imageHint")}</p>
                {selectedImage && (
                  <button
                    className="group-detail__form-submit group-settings__image-upload"
                    disabled={uploadingImage}
                    onClick={() => void uploadGroupImage()}
                    type="button"
                  >
                    {uploadingImage ? t("uploading") : t("uploadImage")}
                  </button>
                )}
                {imageUploadError && (
                  <p className="group-detail__form-error" role="alert">{imageUploadError}</p>
                )}
                {imageUploadMessage && (
                  <p className="group-settings__success" role="status">{imageUploadMessage}</p>
                )}
              </div>
              <div className="group-settings__save">
                <button className="group-detail__form-submit" disabled={saving} type="submit">
                  {saving ? t("saving") : t("saveChanges")}
                </button>
                {settingsError && <p className="group-detail__form-error" role="alert">{settingsError}</p>}
                {settingsMessage && <p className="group-settings__success" role="status">{settingsMessage}</p>}
              </div>
            </form>

            <section aria-labelledby="group-members-title" className="group-settings__card">
              <h2 id="group-members-title">{t("members")}</h2>
              <ul className="group-settings__members">
                {members.map((member, index) => {
                  const deleteIcon = `/group-detail/member-delete-${Math.min(index + 1, 4)}.svg`;
                  return (
                    <li className="group-settings__member" key={member.id}>
                      <span>{member.name}</span>
                      <button
                        aria-label={t("removeMember", { name: member.name })}
                        disabled={removingMemberId !== null || addingMember}
                        onClick={() => void removeMember(member)}
                        type="button"
                      >
                        <Image
                          alt=""
                          height={24}
                          src={deleteIcon}
                          unoptimized
                          width={24}
                        />
                      </button>
                    </li>
                  );
                })}
              </ul>
              <form className="group-settings__add-member" onSubmit={addMember}>
                <label>
                  {t("addMemberLabel")}
                  <input
                    maxLength={100}
                    onChange={(event) => setMemberName(event.target.value)}
                    required
                    value={memberName}
                  />
                </label>
                <button className="group-detail__form-submit" disabled={addingMember} type="submit">
                  {addingMember ? t("adding") : t("addMember")}
                </button>
              </form>
              {memberError && <p className="group-detail__form-error" role="alert">{memberError}</p>}
              {memberMessage && <p className="group-settings__success" role="status">{memberMessage}</p>}
            </section>

            <section aria-labelledby="group-activity-title" className="group-settings__card group-settings__activity">
              <h2 id="group-activity-title">{t("activity")}</h2>
              {activityLogs.length > 0 && (
                <ul className="group-settings__activity-list">
                  {activityLogs.map((log) => (
                    <li className="group-settings__activity-entry" key={log.id}>
                      <span>{describeActivity(log, t)}</span>
                      <time dateTime={log.date}>{formatActivityDate(log.date, locale)}</time>
                    </li>
                  ))}
                </ul>
              )}
              {activityLoadState === "loading" && (
                <p className="group-settings__activity-status" role="status">
                  {activityPage === 0 ? t("loadingActivity") : t("loadingMore")}
                </p>
              )}
              {activityLoadState === "error" && (
                <div className="group-detail__message" role="alert">
                  <p>{activityError}</p>
                  <button
                    onClick={() => setActivityRetryKey((current) => current + 1)}
                    type="button"
                  >
                    {commonT("tryAgain")}
                  </button>
                </div>
              )}
              {activityLoadState === "ready" && activityLogs.length === 0 && (
                <p className="group-settings__activity-status">{t("noActivity")}</p>
              )}
              {activityLoadState === "ready" &&
                activityPage + 1 < activityTotalPages && (
                  <button
                    className="group-settings__activity-more"
                    onClick={() => setActivityPage((current) => current + 1)}
                    type="button"
                  >
                    {t("loadMore")}
                  </button>
                )}
            </section>
          </>
        )}
      </section>
    </main>
  );
}
