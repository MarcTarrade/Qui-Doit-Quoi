"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";

import GroupSectionNav from "@/components/groups/GroupSectionNav";
import { apiRequest, apiRequestWithCsrf } from "@/lib/api";

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

type LoadState = "loading" | "ready" | "error";

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

export default function GroupSettingsPage() {
  const { groupId } = useParams<{ groupId: string }>();
  const imageUrlRef = useRef<HTMLInputElement>(null);
  const [group, setGroup] = useState<Group | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [image, setImage] = useState("");
  const [currency, setCurrency] = useState("");
  const [memberName, setMemberName] = useState("");
  const [saving, setSaving] = useState(false);
  const [addingMember, setAddingMember] = useState(false);
  const [removingMemberId, setRemovingMemberId] = useState<string | null>(null);
  const [settingsMessage, setSettingsMessage] = useState("");
  const [settingsError, setSettingsError] = useState("");
  const [memberError, setMemberError] = useState("");
  const [memberMessage, setMemberMessage] = useState("");

  const retry = useCallback(() => {
    setLoadError("");
    setLoadState("loading");
    setGroup(null);
    setMembers([]);
    setRefreshKey((current) => current + 1);
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
          throw new Error(
            groupResponse.status === 404
              ? "This group could not be found."
              : `Unable to load group details (${groupResponse.status}).`,
          );
        }
        if (!membersResponse.ok) {
          throw new Error(`Unable to load group members (${membersResponse.status}).`);
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
          error instanceof Error ? error.message : "An unexpected error occurred.",
        );
        setLoadState("error");
      }
    }

    void loadSettings();
    return () => controller.abort();
  }, [groupId, refreshKey]);

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
        throw new Error(`Saving group settings failed (${response.status}).`);
      }

      const updatedGroup = parseGroup(await response.json());
      setGroup(updatedGroup);
      setName(updatedGroup.name);
      setDescription(updatedGroup.description);
      setImage(updatedGroup.image);
      setCurrency(updatedGroup.currency);
      setSettingsMessage("Group settings saved.");
    } catch (error) {
      setSettingsError(
        error instanceof Error ? error.message : "An unexpected error occurred.",
      );
    } finally {
      setSaving(false);
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
        throw new Error(`Adding a group member failed (${response.status}).`);
      }

      const newMember = parseMembers([await response.json()])[0];
      setMembers((current) => [...current, newMember]);
      setMemberName("");
      setMemberMessage(`${newMember.name} was added to the group.`);
    } catch (error) {
      setMemberError(
        error instanceof Error ? error.message : "An unexpected error occurred.",
      );
    } finally {
      setAddingMember(false);
    }
  }

  async function removeMember(member: GroupMember) {
    if (
      !window.confirm(`Remove ${member.name} from this group?`)
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
        throw new Error(`Removing a group member failed (${response.status}).`);
      }

      setMembers((current) => current.filter((item) => item.id !== member.id));
      setMemberMessage(`${member.name} was removed from the group.`);
    } catch (error) {
      setMemberError(
        error instanceof Error ? error.message : "An unexpected error occurred.",
      );
    } finally {
      setRemovingMemberId(null);
    }
  }

  return (
    <main className="group-detail">
      <header className="group-detail__header">
        <Link
          aria-label="Back to groups"
          className="group-detail__back"
          href="/"
        >
          <Image alt="" height={32} src="/group-detail/back-mobile.svg" width={32} />
        </Link>
        <h1>{group?.name}</h1>
      </header>

      <GroupSectionNav activeSection="settings" groupId={groupId} />

      <section aria-label="Group settings" className="group-detail__content group-settings">
        {loadState === "loading" && (
          <p className="group-detail__status" role="status">Loading group settings…</p>
        )}
        {loadState === "error" && (
          <div className="group-detail__message" role="alert">
            <p>{loadError}</p>
            <button onClick={retry} type="button">Try again</button>
          </div>
        )}
        {loadState === "ready" && (
          <>
            <form className="group-settings__card group-settings__information" onSubmit={saveSettings}>
              <h2>Information</h2>
              <div className="group-settings__fields">
                <label>
                  Group name
                  <input
                    maxLength={100}
                    onChange={(event) => setName(event.target.value)}
                    required
                    value={name}
                  />
                </label>
                <label>
                  Currency
                  <input
                    autoCapitalize="characters"
                    maxLength={3}
                    onChange={(event) => setCurrency(event.target.value.toUpperCase())}
                    pattern="[A-Z]{3}"
                    required
                    title="Enter a 3-letter ISO currency code, such as EUR or USD."
                    value={currency}
                  />
                </label>
                <label className="group-settings__description">
                  Description
                  <textarea
                    maxLength={1000}
                    onChange={(event) => setDescription(event.target.value)}
                    rows={4}
                    value={description}
                  />
                </label>
              </div>
              <div className="group-settings__image">
                <span className="group-settings__image-title">Group image</span>
                <button
                  aria-label="Edit group image URL"
                  className="group-settings__image-preview"
                  onClick={() => imageUrlRef.current?.focus()}
                  type="button"
                >
                  <Image
                    alt=""
                    className="group-settings__image-picture"
                    height={170}
                    src={image || "/group-detail/group-image-placeholder.svg"}
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
                <label>
                  Image URL
                  <input
                    maxLength={2048}
                    onChange={(event) => setImage(event.target.value)}
                    ref={imageUrlRef}
                    type="url"
                    value={image}
                  />
                </label>
              </div>
              <div className="group-settings__save">
                {settingsError && <p className="group-detail__form-error" role="alert">{settingsError}</p>}
                {settingsMessage && <p className="group-settings__success" role="status">{settingsMessage}</p>}
                <button className="group-detail__form-submit" disabled={saving} type="submit">
                  {saving ? "Saving…" : "Save changes"}
                </button>
              </div>
            </form>

            <section aria-labelledby="group-members-title" className="group-settings__card">
              <h2 id="group-members-title">Group members</h2>
              <ul className="group-settings__members">
                {members.map((member, index) => {
                  const deleteIcon = `/group-detail/member-delete-${Math.min(index + 1, 4)}.svg`;
                  return (
                    <li className="group-settings__member" key={member.id}>
                      <span>{member.name}</span>
                      <button
                        aria-label={`Remove ${member.name}`}
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
                  Add a member
                  <input
                    maxLength={100}
                    onChange={(event) => setMemberName(event.target.value)}
                    required
                    value={memberName}
                  />
                </label>
                <button className="group-detail__form-submit" disabled={addingMember} type="submit">
                  {addingMember ? "Adding…" : "Add member"}
                </button>
              </form>
              {memberError && <p className="group-detail__form-error" role="alert">{memberError}</p>}
              {memberMessage && <p className="group-settings__success" role="status">{memberMessage}</p>}
            </section>

            <section aria-labelledby="group-activity-title" className="group-settings__card group-settings__activity">
              <h2 id="group-activity-title">Activity log</h2>
              <p>Activity history is not available yet.</p>
            </section>
          </>
        )}
      </section>
    </main>
  );
}
