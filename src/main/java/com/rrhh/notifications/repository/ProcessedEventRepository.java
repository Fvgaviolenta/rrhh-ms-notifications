package com.rrhh.notifications.repository;
import com.rrhh.notifications.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent,String>{}
