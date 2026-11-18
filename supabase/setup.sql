-- Run once in Supabase Dashboard > SQL Editor.
-- Authentication > Providers > Email: disable email confirmation for the full
-- three-step registration demo, otherwise the client receives no session on signup.

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    surname text not null,
    first_name text not null,
    patronymic text,
    birth_date date not null,
    gender text not null check (gender in ('Мужской', 'Женский')),
    license_number text not null check (char_length(license_number) = 10),
    issue_date date not null,
    avatar_path text,
    license_photo_path text not null,
    passport_photo_path text not null,
    created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
grant select, insert, update on public.profiles to authenticated;

drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own" on public.profiles
    for select to authenticated using (id = auth.uid());
drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles
    for insert to authenticated with check (id = auth.uid());
drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
    for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('identity-documents', 'identity-documents', false, 10485760,
        array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = 10485760,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "identity_docs_select_own" on storage.objects;
create policy "identity_docs_select_own" on storage.objects
    for select to authenticated using (
        bucket_id = 'identity-documents' and (storage.foldername(name))[1] = auth.uid()::text
    );
drop policy if exists "identity_docs_insert_own" on storage.objects;
create policy "identity_docs_insert_own" on storage.objects
    for insert to authenticated with check (
        bucket_id = 'identity-documents' and (storage.foldername(name))[1] = auth.uid()::text
    );
drop policy if exists "identity_docs_update_own" on storage.objects;
create policy "identity_docs_update_own" on storage.objects
    for update to authenticated using (
        bucket_id = 'identity-documents' and (storage.foldername(name))[1] = auth.uid()::text
    ) with check (
        bucket_id = 'identity-documents' and (storage.foldername(name))[1] = auth.uid()::text
    );
